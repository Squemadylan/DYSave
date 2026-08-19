package com.douyin.downloader.data.remote

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通过 Root（APatch / Magisk 的 `su`）读取已安装 App 的 WebView Cookie 数据库，
 * 提取指定域的会话 Cookie 并拼成 `name=value; name=value; ...` 字符串。
 *
 * 与 [ShizukuCookieFetcher]（读 SharedPrefs XML）不同，WebView 的 Cookie 存在
 * app_webview 目录下的 Default/Cookies（或 app_webview/Cookies）这个 SQLite 库里，
 * 所以这里直接用 `su` 把库 `cat` 出来，再用 [SQLiteDatabase] 查 `cookies` 表。
 *
 * 使用前提：本 App 已被授予 Root 权限（用户已在 APatch / Magisk 中授权给 DYSave）。
 *
 * 设计：
 * - 抖音：优先读「抖音 App」(`com.ss.android.ugc.aweme`) 的 WebView —— 只要抖音 App 已登录，
 *   即可一键拿到 `sessionid_ss` / `sid_guard` / `sid_tt` / `ttwid` 等，无需在 DYSave 内再登录。
 * - 小红书：小红书 App 不向外暴露 Web Cookie（已验证其数据目录无 `web_session` 等），
 *   因此读「本 App 自己的 WebView」(`com.douyin.downloader`) —— 需用户先在
 *   「登录获取」里完成一次小红书网页登录，Cookie 即落入本 App 的 WebView，随后可被一键读取。
 *
 * 注意点：
 * - 用绝对路径 `/system/bin/find`、`/system/bin/cat`，避免 su 环境下 PATH 不完整。
 * - 用 ProcessBuilder 并把 stderr 合并到 stdout，避免大文件（抖音 Cookies ~390KB）读取时
 *   因管道缓冲导致死锁。
 * - 目标 App（如抖音）常驻后台、其 Cookies 库可能正在被写入，cat 瞬间可能读到半截页导致
 *   SQLite 打开失败，这里对「解析」阶段做 3 次重试以提高成功率。
 */
@Singleton
class RootCookieFetcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "RootCookieFetcher"
        const val DOUYIN_PKG = "com.ss.android.ugc.aweme"
        const val XHS_PKG = "com.xingin.xhs"
        const val SELF_PKG = "com.douyin.downloader"
        private const val FIND = "/system/bin/find"
        private const val CAT = "/system/bin/cat"
    }

    /** 最近一次失败原因，供 UI Toast 展示。 */
    var lastError: String? = null
        private set

    /** 检测 Root 是否可用（su 是否返回 uid 0）。 */
    suspend fun hasRoot(): Boolean = withContext(Dispatchers.IO) {
        runSu("id")?.contains("uid=0") == true
    }

    /**
     * 获取抖音 Cookie 字符串。
     * 优先读抖音 App 的 WebView（已登录即可），回退到本 App 的 WebView。
     */
    suspend fun fetchDouyinCookie(): String? = withContext(Dispatchers.IO) {
        fetchFromPackage(DOUYIN_PKG, listOf("%douyin.com%", "%iesdouyin.com%"))
            ?: fetchFromPackage(SELF_PKG, listOf("%douyin.com%", "%iesdouyin.com%"))
    }

    /**
     * 获取小红书 Cookie 字符串。
     * 读本 App 的 WebView（需先在「登录获取」完成小红书网页登录），
     * 回退到小红书 App（其通常无 Web Cookie，基本拿不到登录态）。
     */
    suspend fun fetchXhsCookie(): String? = withContext(Dispatchers.IO) {
        fetchFromPackage(SELF_PKG, listOf("%xiaohongshu.com%"))
            ?: fetchFromPackage(XHS_PKG, listOf("%xiaohongshu.com%"))
    }

    private fun fetchFromPackage(pkg: String, hostLikes: List<String>): String? {
        lastError = null
        val cookiesPath = resolveCookiesPath(pkg)
        if (cookiesPath == null) {
            val msg = "$pkg 未找到 WebView Cookies 文件（su 可能未授权，或路径不存在）"
            lastError = msg
            Log.w(TAG, msg)
            return null
        }
        // 目标库可能正在被目标 App 写入，cat 瞬间可能读到半截导致 SQLite 打开失败，
        // 这里重试几次以提高成功率。
        var lastEx: Exception? = null
        for (attempt in 1..3) {
            try {
                val bytes = runSuBytes("$CAT \"$cookiesPath\"")
                if (bytes == null || bytes.isEmpty()) {
                    val msg = "$pkg 读取 Cookies 为空（su 返回空，可能无 Root 权限或被 SELinux 拦截）"
                    lastError = msg
                    Log.w(TAG, msg)
                    return null
                }
                val tmp = File(context.cacheDir, "root_cookies_${pkg.replace('.', '_')}_$attempt.db")
                try {
                    tmp.writeBytes(bytes)
                    val db = SQLiteDatabase.openDatabase(tmp.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                    val sb = StringBuilder()
                    for (like in hostLikes) {
                        db.rawQuery(
                            "SELECT name, value FROM cookies WHERE host_key LIKE ?",
                            arrayOf(like),
                        ).use { c ->
                            while (c.moveToNext()) {
                                val name = c.getString(0) ?: continue
                                val value = c.getString(1) ?: ""
                                if (name.isBlank()) continue
                                if (sb.isNotEmpty()) sb.append("; ")
                                sb.append(name).append('=').append(value)
                            }
                        }
                    }
                    db.close()
                    if (sb.isNotBlank()) {
                        Log.d(TAG, "$pkg Cookie 提取成功(${sb.length}字符)")
                        return sb.toString()
                    }
                    // 文件可读但无匹配域名的 Cookie，重试无意义，直接返回 null
                    val msg = "$pkg 已读取，但无匹配 $hostLikes 的 Cookie（可能对应 App 未登录）"
                    lastError = msg
                    Log.w(TAG, msg)
                    return null
                } finally {
                    tmp.delete()
                }
            } catch (e: Exception) {
                lastEx = e
                Log.w(TAG, "解析 $pkg Cookies 第$attempt 次失败: ${Log.getStackTraceString(e)}")
                try {
                    Thread.sleep(150)
                } catch (_: InterruptedException) {
                }
            }
        }
        val failMsg = "解析 $pkg Cookies 多次失败: ${lastEx?.message}"
        lastError = failMsg
        Log.e(TAG, failMsg)
        return null
    }

    /**
     * 找到目标包的 WebView 主 Cookies 文件路径。
     * 用 find 而非 ls 通配（APatch 的 su 会吞掉通配符 `*`）。
     * 过滤掉带 `:` 的子 profile（如 `:minigame0`、`:miniappX`），只取主 WebView profile。
     */
    private fun resolveCookiesPath(pkg: String): String? {
        val out = runSu("$FIND /data/data/$pkg -name Cookies 2>/dev/null") ?: return null
        return out.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.contains(':') }
            .firstOrNull()
    }

    private fun runSu(cmd: String): String? =
        runSuBytes(cmd)?.let { String(it, Charsets.UTF_8) }

    /**
     * 以 root 执行命令，返回 stdout 字节（失败返回 null）。
     *
     * 关键点（已在真机 nsenter 实证）：Android 11+ 的「App 数据隔离」让每个 App 运行在**独立
     * mount 命名空间**——在 DYSave 自己的命名空间里，别的 App 的 `/data/data/<pkg>` 会被隐藏成
     * 「No such file or directory」。App 内 spawn 的 su 继承了本 App 的命名空间，所以能读**自己**
     * 目录（小红书成功），却**读不到抖音等其它 App**的目录。
     * 修复：用 `su -M`（--mount-master，强制进入全局 mount 命名空间），即可看到所有 App 的数据。
     * 因此优先用 `-M -c`；若某些 su 实现不认 `-M`，再回退普通 `-c`。
     * APatch 的 su **不支持** `-Z/--context`（实测报 Unrecognized option），故不使用。
     */
    private fun runSuBytes(cmd: String): ByteArray? {
        val suPaths = listOf("su", "/system/bin/su", "/system/xbin/su", "/data/adb/ap/bin/su")
        // 选项变体：优先 -M（全局命名空间，跨 App 读取必需），失败再回退普通 -c
        val optionSets = listOf(
            listOf("-M", "-c"),
            listOf("-c"),
        )
        var lastErr: String? = null
        for (su in suPaths) {
            for (opts in optionSets) {
                val argv = (listOf(su) + opts + listOf(cmd)).toTypedArray()
                val label = (listOf(su) + opts).joinToString(" ")
                try {
                    val pb = ProcessBuilder(*argv)
                    pb.redirectErrorStream(true) // 合并 stderr，避免大输出死锁
                    val process = pb.start()
                    val out = process.inputStream.readBytes()
                    val code = process.waitFor()
                    if (code != 0) {
                        lastErr = "su[$label] 失败($code): ${String(out, Charsets.UTF_8).take(160)}"
                        Log.w(TAG, lastErr)
                        continue
                    }
                    Log.d(TAG, "su 成功[$label] out=${out.size}B")
                    return out
                } catch (e: Exception) {
                    lastErr = "su[$label] 异常: ${e.message}"
                    Log.w(TAG, lastErr)
                }
            }
        }
        Log.w(TAG, "所有 su 变体均失败: $lastErr")
        return null
    }
}
