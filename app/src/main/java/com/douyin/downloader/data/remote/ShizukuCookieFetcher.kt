package com.douyin.downloader.data.remote

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Base64
import android.util.Log
import com.douyin.downloader.IUserService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通过 Shizuku (run-as) 读取已安装 App 的 SharedPrefs Cookie。
 *
 * 原理：
 * 1. 用户安装 Shizuku App 并通过 ADB 授权
 * 2. DySave 通过 Shizuku API 请求权限
 * 3. 授权后绑定 UserService（以 shell 身份运行）
 * 4. UserService 执行 `run-as <pkg>` 命令读取 SharedPrefs XML
 * 5. 解析 XML，提取 Cookie 字段
 *
 * 支持包名：
 * - 抖音：`com.ss.android.ugc.aweme`
 *   SharedPrefs：ttnetCookieStore.xml（Base64 编码）
 * - 小红书：`com.xingin.xhs`
 *   SharedPrefs：cookie 相关 xml
 */
@Singleton
class ShizukuCookieFetcher @Inject constructor(
    private val context: Context,
) {

    companion object {
        private const val TAG = "ShizukuCookieFetcher"
        const val DOUYIN_PKG = "com.ss.android.ugc.aweme"
        const val XHS_PKG = "com.xingin.xhs"
        private const val SHIZUKU_PERMISSION_CODE = 10001

        // UserService 参数：tag 用于 Shizuku 判断是否为同一服务实例
        private val USER_SERVICE_ARGS = Shizuku.UserServiceArgs(
            ComponentName(
                "com.douyin.downloader",
                "com.douyin.downloader.data.remote.UserService"
            )
        ).daemon(false).processNameSuffix("dy_cookie_service")
    }

    private var userService: IUserService? = null
    private var serviceConnection: ServiceConnection? = null
        set(value) {
            // 自动解绑旧连接
            field?.let { Shizuku.unbindUserService(USER_SERVICE_ARGS, it, true) }
            field = value
        }

    // ===== 公开 API =====

    /** 检查 Shizuku 是否可用（服务运行中且已授权） */
    fun isAvailable(): Boolean {
        return try {
            val uid = Shizuku.getUid()
            val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "isAvailable: uid=$uid granted=$granted")
            uid >= 0 && granted
        } catch (e: Exception) {
            Log.w(TAG, "isAvailable failed: ${e.message}")
            false
        }
    }

    /**
     * 请求 Shizuku 权限。
     * 返回 false 表示需要用户确认（会弹出授权对话框）。
     */
    fun requestPermission(): Boolean {
        return try {
            when (val result = Shizuku.checkSelfPermission()) {
                PackageManager.PERMISSION_GRANTED -> true
                else -> {
                    Shizuku.addRequestPermissionResultListener(object :
                        Shizuku.OnRequestPermissionResultListener {
                        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                            if (requestCode == SHIZUKU_PERMISSION_CODE) {
                                Log.d(TAG, "权限结果: grantResult=$grantResult")
                                Shizuku.removeRequestPermissionResultListener(this)
                                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                                    connectUserService()
                                }
                            }
                        }
                    })
                    Shizuku.requestPermission(SHIZUKU_PERMISSION_CODE)
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "requestPermission failed: ${e.message}")
            false
        }
    }

    /**
     * 读取指定包的 Cookie，拼接为 Cookie 字符串。
     *
     * @param pkg 包名
     * @return Cookie 字符串，失败返回 null
     */
    suspend fun fetchCookies(pkg: String): String? = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            Log.w(TAG, "Shizuku 不可用，无法读取 $pkg")
            return@withContext null
        }

        if (userService == null) {
            val connected = connectWithRetry()
            if (!connected) {
                Log.w(TAG, "UserService 连接失败，放弃读取 $pkg")
                return@withContext null
            }
        }

        return@withContext try {
            // Step 1: 列出 SharedPrefs
            val files = userService?.listSharedPrefs(pkg)
                ?.split("\n")
                ?.map { it.trim() }
                ?.filter { it.endsWith(".xml") && it.isNotEmpty() }
                .orEmpty()

            if (files.isEmpty()) {
                Log.w(TAG, "$pkg 无 SharedPrefs")
                return@withContext null
            }
            Log.d(TAG, "找到 ${files.size} 个文件: $files")

            // Step 2: 读取并解析所有 XML
            val allEntries = mutableListOf<Pair<String, String>>()
            for (file in files) {
                val content = userService?.readSharedPrefsFile(pkg, file)
                if (content != null) {
                    allEntries.addAll(parseXml(content, file))
                }
            }

            // Step 3: 筛选 Cookie 字段并拼接
            val cookieMap = allEntries.toMap()
            val cookieFields = cookieMap.filterKeys { isCookieField(it) }

            if (cookieFields.isEmpty()) {
                Log.w(TAG, "$pkg 无 Cookie 字段")
                null
            } else {
                val cookieStr = cookieFields.entries.joinToString("; ") { (k, v) -> "$k=$v" }
                Log.d(TAG, "✅ $pkg Cookie 读取成功，字段数=${cookieFields.size}")
                cookieStr
            }
        } catch (e: Exception) {
            Log.e(TAG, "读取 $pkg Cookie 失败: ${e.message}", e)
            null
        }
    }

    /** 检查目标包是否已安装 */
    fun isPackageInstalled(pkg: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(pkg, 0) != null
        } catch (e: Exception) {
            false
        }
    }

    // ===== 私有方法 =====

    private fun connectUserService() {
        if (serviceConnection != null) return
        val conn = object : ServiceConnection {
            override fun onServiceConnected(component: ComponentName, binder: IBinder) {
                Log.d(TAG, "UserService 连接成功")
                userService = IUserService.Stub.asInterface(ShizukuBinderWrapper(binder))
                serviceConnection = null // 转移所有权，unbind 由字段 setter 处理
            }

            override fun onServiceDisconnected(component: ComponentName) {
                Log.d(TAG, "UserService 断开连接")
                userService = null
            }
        }
        serviceConnection = conn
        try {
            Shizuku.bindUserService(USER_SERVICE_ARGS, conn)
            Log.d(TAG, "正在连接 UserService...")
        } catch (e: Exception) {
            Log.e(TAG, "连接 UserService 失败: ${e.message}", e)
            serviceConnection = null
        }
    }

    /**
     * 带延迟和重试的连接 UserService。
     * 原因：Shizuku 授权后需要一些时间让 server binder 可用，立即调用会失败。
     * 最多重试 3 次，每次间隔 500ms。
     */
    suspend fun connectWithRetry(maxRetries: Int = 3, delayMs: Long = 500L): Boolean = withContext(Dispatchers.Main) {
        repeat(maxRetries) { attempt ->
            if (attempt > 0) {
                Log.d(TAG, "重试连接 UserService (第 ${attempt + 1} 次)...")
                kotlinx.coroutines.delay(delayMs)
            }
            connectUserService()
            // 等待连接回调（最多 2 秒）
            val connected = waitForConnection(timeoutMs = 2000)
            if (connected) {
                Log.d(TAG, "UserService 连接成功（第 ${attempt + 1} 次尝试）")
                return@withContext true
            }
            Log.w(TAG, "UserService 连接超时，准备重试...")
        }
        Log.e(TAG, "UserService 连接失败，已重试 $maxRetries 次")
        false
    }

    /**
     * 等待 UserService 连接回调。
     * 返回 true 表示连接成功，false 表示超时。
     */
    private suspend fun waitForConnection(timeoutMs: Long): Boolean {
        return kotlinx.coroutines.withTimeout(timeoutMs) {
            while (userService == null) {
                kotlinx.coroutines.delay(100)
            }
            true
        }
    }

    private fun parseXml(xml: String, filename: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val entryRegex = Regex(
            """<string\s+name="([^"]+)"(?:\s+[^>]*)?>(.*?)</string>""",
            RegexOption.DOT_MATCHES_ALL
        )
        entryRegex.findAll(xml).forEach { match ->
            val key = match.groupValues[1]
            var value = match.groupValues[2]
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")

            // 尝试 Base64 解码（抖音 ttnetCookieStore.xml）
            if (filename.contains("cookie", ignoreCase = true) ||
                filename.contains("token", ignoreCase = true) ||
                filename.contains("ss", ignoreCase = true)) {
                try {
                    val decoded = Base64.decode(value, Base64.NO_WRAP)
                    if (decoded.isNotEmpty() && decoded.all { it.toByte() in 32..126 || it.toByte() in 10..13 }) {
                        val decodedStr = String(decoded, Charsets.UTF_8)
                        if (decodedStr.contains("=") && decodedStr.contains(".")) {
                            value = decodedStr
                            Log.d(TAG, "Base64 解码成功: $key")
                        }
                    }
                } catch (_: Exception) {
                    // 不是 Base64，保持原文
                }
            }
            result.add(key to value)
        }
        return result
    }

    private fun isCookieField(key: String): Boolean {
        val lowerKey = key.lowercase()
        return lowerKey.contains("cookie") ||
            lowerKey.contains("token") ||
            lowerKey.contains("session") ||
            lowerKey.contains("sid") ||
            lowerKey.contains("uid") ||
            key == "device_id" ||
            key == "install_id" ||
            key == "ttid"
    }
}
