package com.douyin.downloader.data.remote

import com.douyin.downloader.data.model.DownloadException
import com.douyin.downloader.data.model.ParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
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
     *
     * 注意：2026 之后该短链跳到 www.douyin.com 的 aweme-share 页面，
     * 新页没有内嵌 _ROUTER_DATA（数据要 a_bogus 签名）。调用方应当再
     * 走 [resolveToShareablePage] 拿一个能在 iesdouyin.com 旧模板下取到数据的 URL。
     */
    suspend fun resolveShareUrl(url: String): String = withContext(Dispatchers.IO) {
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
     *
     * 策略：
     * 1) 短链先 HEAD 跟随拿到最终页 URL；
     * 2) 如果最终页是 www.douyin.com / m.douyin.com / aweme-share 之类新模板，
     *    GET 拿 HTML，从 <link rel="canonical"> 或 URL 路径里抽 aweme_id；
     * 3) 返回 https://www.iesdouyin.com/share/video/{id}/ 这种旧模板 URL。
     *
     * 失败时回退到原 URL，让 ContentRepository 的 extractIds 自行再尝试。
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

    suspend fun fetchPage(url: String): String = withContext(Dispatchers.IO) {
        // 抖音 WAF 概率性拦截：3 次重试，第 2 次换 Android UA 兜底
        // attempt: 0=iOS, 1=Android(等 800ms), 2=iOS(等 1500ms)
        val backoffsMs = longArrayOf(0L, 800L, 1500L)
        val uas = arrayOf(USER_AGENT, USER_AGENT_ANDROID, USER_AGENT)
        var lastError: Exception? = null
        for (attempt in backoffsMs.indices) {
            if (backoffsMs[attempt] > 0L) {
                kotlinx.coroutines.delay(backoffsMs[attempt])
            }
            try {
                val html = fetchPageOnce(url, uas[attempt])
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
            // 调试 log：标记命中/失败
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