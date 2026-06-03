package com.douyin.downloader.data.remote

import com.douyin.downloader.data.model.DownloadException
import com.douyin.downloader.data.model.ParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DouyinApi @Inject constructor(private val client: OkHttpClient) {

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 " +
                "Mobile/15E148 Safari/604.1"
        // 兜底 UA：iOS 被风控时换 Android UA 再试一次
        private const val USER_AGENT_ANDROID =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 " +
                "Mobile Safari/537.36"
        private const val REFERER = "https://www.douyin.com/"

        // —— 限流 / 抗封配置 ——
        // 令牌桶：平均 1.5 req/s（66ms/req），burst 5
        private const val MIN_INTERVAL_MS = 700L
        // 解析结果缓存：同一 aweme_id 5 分钟内复用
        private const val PAGE_CACHE_TTL_MS = 5 * 60 * 1000L
        // 检测到 WAF/黑名单后，全局冷却 30s（期间所有 fetchPage 直接 wait）
        private const val WAF_COOLDOWN_MS = 30_000L
        // 重试总退避上限
        private const val MAX_BACKOFF_MS = 15_000L

        /**
         * 从 iesdouyin 页面 HTML 里挑出最终目标 id（video/ 数字 /）。
         * 抖音 2026 之后 v.douyin.com 短链重定向到 www.douyin.com/aweme-share/video/{id}/
         * 那个新页没有内嵌 _ROUTER_DATA（数据靠 JS 异步拉取，要 a_bogus 签名），
         * 拿不到。退而求其次：把 aweme_id 抠出来，再去 iesdouyin.com/share/video/{id}/
         * —— 那个老模板页 iOS UA 能稳定返回 _ROUTER_DATA。
         */
        private val AWEME_ID_REGEXES = listOf(
            Regex("""/video/(\d{10,})"""),
            Regex("""/note/(\d{10,})"""),
            Regex("""item_ids=(\d{10,})"""),
            Regex("""aweme_id[\\"'\s:=]+(\d{10,})"""),
        )
    }

    // —— 限流状态（process 级，单例）——
    private val rateMutex = Mutex()
    @Volatile private var lastRequestAt: Long = 0L
    // WAF 全局冷却截止时间（SystemClock.elapsedRealtime）
    @Volatile private var wafCooldownUntil: Long = 0L
    // 解析结果缓存：URL -> (html, expireAt)
    private val pageCache = ConcurrentHashMap<String, Pair<String, Long>>()

    /**
     * 从 www.douyin.com 短链重定向后的页里抠出 aweme_id（video 或 note）。
     * 仅在重定向到 douyin.com 系列域名时调用。
     */
    internal fun extractAwemeIdFromFinalPage(html: String): String? {
        for (r in AWEME_ID_REGEXES) {
            r.find(html)?.let { return it.groupValues[1] }
        }
        return null
    }

    /**
     * 跟随 v.douyin.com 短链跳转，返回最终页 URL。
     */
    suspend fun resolveShareUrl(url: String): String = withContext(Dispatchers.IO) {
        acquireToken()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .head()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ParseException.UrlResolveFailed("短链接跳转失败，HTTP ${response.code}")
            response.request.url.toString()
        }
    }

    /**
     * 把任意抖音页 URL 转换成一个能在 iesdouyin 旧模板里取到 _ROUTER_DATA 的
     * "可分享" 链接。
     */
    suspend fun resolveToShareablePage(url: String): String = withContext(Dispatchers.IO) {
        // 1) 短链先跟随 redirect
        val finalUrl = try {
            if ("v.douyin.com" in url || "v.iesdouyin.com" in url) resolveShareUrl(url) else url
        } catch (e: Exception) {
            url
        }

        // 2) 已经是 iesdouyin 直接用
        if ("iesdouyin.com" in finalUrl) return@withContext finalUrl

        // 3) 拿 HTML 抠 aweme_id
        acquireToken()
        val request = Request.Builder()
            .url(finalUrl)
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext finalUrl
            val html = response.body?.string().orEmpty()
            // 优先 canonical
            val canonical = Regex("""<link[^>]+rel=["']canonical["'][^>]+href=["'](https?://[^"']+)""")
                .find(html)?.groupValues?.get(1).orEmpty()
            val id = extractAwemeIdFromFinalPage(canonical)
                ?: extractAwemeIdFromFinalPage(finalUrl)
                ?: extractAwemeIdFromFinalPage(html)
            if (id != null) {
                // 优先 video 模板（图集 note 也走 video 模板同源，HtmlParser 会再分流）
                "https://www.iesdouyin.com/share/video/$id/"
            } else {
                finalUrl
            }
        }
    }

    /**
     * 取一个令牌：保证两次请求至少间隔 MIN_INTERVAL_MS，
     * 并在 WAF 冷却期间 wait。
     */
    private suspend fun acquireToken() {
        rateMutex.withLock {
            val now = android.os.SystemClock.elapsedRealtime()
            // 1) WAF 冷却：等到截止时间
            if (wafCooldownUntil > now) {
                val wait = wafCooldownUntil - now
                android.util.Log.w("DouyinApi", "WAF 冷却中，wait ${wait}ms")
                delay(wait)
            }
            // 2) 速率限制：两次请求至少 MIN_INTERVAL_MS
            val sinceLast = now - lastRequestAt
            if (lastRequestAt > 0 && sinceLast < MIN_INTERVAL_MS) {
                delay(MIN_INTERVAL_MS - sinceLast)
            }
            lastRequestAt = android.os.SystemClock.elapsedRealtime()
        }
    }

    /**
     * 检测响应内容是否被 WAF 拦截或被换成了"空壳"页（缺 _ROUTER_DATA）。
     * 注意：iesdouyin 旧模板 200 OK 但不含 _ROUTER_DATA = 服务端拒绝/限流。
     */
    private fun looksLikeWafBlock(html: String): Boolean {
        if (html.isEmpty()) return true
        if (html.contains("WAFJS") || html.contains("out-sha256") || html.contains("acrawler")) {
            return true
        }
        // 200 OK 但没有 _ROUTER_DATA 且长度短 = 限流空壳页
        if (!html.contains("_ROUTER_DATA") && html.length < 50_000) {
            return true
        }
        return false
    }

    private fun markWafCooldown() {
        val until = android.os.SystemClock.elapsedRealtime() + WAF_COOLDOWN_MS
        wafCooldownUntil = until
        android.util.Log.w("DouyinApi", "检测到 WAF 限流，全局冷却 ${WAF_COOLDOWN_MS}ms")
    }

    /**
     * 拉取 HTML 页面，带：
     *  - 5 分钟内同 URL 复用
     *  - 全局令牌桶限流 1.5 req/s
     *  - 指数退避重试（第 2 次换 Android UA 兜底）
     *  - 命中 WAF 自动全局冷却
     */
    suspend fun fetchPage(url: String): String = withContext(Dispatchers.IO) {
        // 1) 缓存命中
        pageCache[url]?.let { (cached, expireAt) ->
            if (expireAt > android.os.SystemClock.elapsedRealtime()) {
                return@withContext cached
            } else {
                pageCache.remove(url)
            }
        }

        // 2) 重试循环（最多 3 次，指数退避 0/2/4s，封顶 15s）
        val backoffsMs = longArrayOf(0L, 2_000L, 4_000L)
        val uas = arrayOf(USER_AGENT, USER_AGENT_ANDROID, USER_AGENT)
        var lastError: Exception? = null
        for (attempt in backoffsMs.indices) {
            if (backoffsMs[attempt] > 0L) {
                delay(backoffsMs[attempt])
            }
            try {
                acquireToken()
                val html = fetchPageOnce(url, uas[attempt])
                // WAF 命中检测：触发全局冷却 + 不写缓存
                if (looksLikeWafBlock(html)) {
                    markWafCooldown()
                    lastError = ParseException.PageFetchFailed("页面被 WAF 限流（_ROUTER_DATA 缺失）")
                    android.util.Log.w(
                        "DouyinApi",
                        "fetchPage WAF 命中 attempt=${attempt + 1}/${backoffsMs.size} url=$url len=${html.length}",
                    )
                    continue
                }
                // 成功：写缓存
                pageCache[url] = html to (android.os.SystemClock.elapsedRealtime() + PAGE_CACHE_TTL_MS)
                if (attempt > 0) {
                    android.util.Log.d(
                        "DouyinApi",
                        "fetchPage 重试成功 attempt=${attempt + 1} ua=${
                            if (uas[attempt] == USER_AGENT) "iOS" else "Android"
                        } url=$url",
                    )
                }
                return@withContext html
            } catch (e: ParseException.PageFetchFailed) {
                lastError = e
                android.util.Log.w(
                    "DouyinApi",
                    "fetchPage 失败 attempt=${attempt + 1}/${backoffsMs.size} ua=${
                        if (uas[attempt] == USER_AGENT) "iOS" else "Android"
                    } url=$url err=${e.message}",
                )
            }
        }
        throw lastError ?: ParseException.PageFetchFailed("页面加载失败")
    }

    private fun fetchPageOnce(url: String, userAgent: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Referer", REFERER)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ParseException.PageFetchFailed("页面加载失败，HTTP ${response.code}，链接可能已失效")
            }
            val html = response.body?.string() ?: throw ParseException.PageFetchFailed("页面返回内容为空")
            val hasRouter = html.contains("_ROUTER_DATA")
            val hasWaf = html.contains("WAFJS") || html.contains("out-sha256")
            android.util.Log.d(
                "DouyinApi",
                "fetchPage url=$url ua=${
                    if (userAgent == USER_AGENT) "iOS" else "Android"
                } len=${html.length} _ROUTER_DATA=$hasRouter waf=$hasWaf",
            )
            return html
        }
    }

    suspend fun downloadBytes(url: String, timeoutSeconds: Int = 30): ByteArray =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .build()
            client.newBuilder()
                .connectTimeout(timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw DownloadException.DownloadFailed("文件下载失败，HTTP ${response.code}")
                    response.body?.bytes() ?: throw DownloadException.DownloadFailed("文件内容为空")
                }
        }

    suspend fun downloadWithProgress(
        url: String,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw DownloadException.DownloadFailed("文件下载失败，HTTP ${response.code}")
            val body = response.body ?: throw DownloadException.DownloadFailed("文件内容为空")
            val total = body.contentLength().takeIf { it != -1L }
            val input = body.byteStream()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var downloaded = 0L
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                downloaded += read
                onProgress(downloaded, total)
            }
            output.toByteArray()
        }
    }

    suspend fun streamTo(
        url: String,
        output: java.io.OutputStream,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> },
    ): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .build()
        client.newBuilder()
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()
            .newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw DownloadException.DownloadFailed("视频流下载失败，HTTP ${response.code}")
                val body = response.body ?: throw DownloadException.DownloadFailed("视频流为空")
                val total = body.contentLength().takeIf { it != -1L }
                val input = body.byteStream()
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, total)
                }
                downloaded
            }
    }
}