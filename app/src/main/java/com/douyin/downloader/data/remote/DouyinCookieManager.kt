package com.douyin.downloader.data.remote

import android.content.Context
import android.webkit.CookieManager
import android.webkit.CookieSyncManager
import java.util.*

/**
 * 抖音 Cookie 管理器。
 *
 * 功能：
 * 1. 读取当前 Cookie
 * 2. 检测登录态（sessionid_ss/sid_guard/sid_tt）
 * 3. 清除 Cookie
 * 4. 检查 Cookie 是否即将过期
 */
class DouyinCookieManager(private val context: Context) {

    companion object {
        private const val TAG = "DouyinCookieManager"
        private const val DOUYIN_DOMAIN = "www.douyin.com"
        private const val IESDOUYIN_DOMAIN = "www.iesdouyin.com"

        /** Cookie 预警阈值：剩余 7 天到期时提示刷新 */
        const val COOKIE_WARN_DAYS = 7

        /** 抖音关键 Cookie 字段（登录态检测） */
        private val LOGIN_INDICATOR_FIELDS = listOf(
            "sessionid_ss", "sid_guard", "sid_tt", "msToken"
        )

        /** 抖音 Cookie 关键字段（请求时需要） */
        private val REQUIRED_COOKIE_FIELDS = listOf(
            "ttwid", "sessionid_ss", "msToken", "sid_guard"
        )
    }

    /** 读取当前 Cookie 字符串 */
    fun getCookie(): String {
        val cm = CookieManager.getInstance()
        val cookies = mutableListOf<String>()

        for (domain in listOf(DOUYIN_DOMAIN, IESDOUYIN_DOMAIN)) {
            try {
                val cookie = cm.getCookie("https://$domain")
                if (!cookie.isNullOrBlank()) {
                    cookies.add(cookie)
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        return cookies.joinToString("; ").trim()
    }

    /** 检查是否已登录（存在 sessionid_ss 或 sid_guard） */
    fun isLoggedIn(cookie: String? = null): Boolean {
        val cookieStr = cookie ?: getCookie()
        if (cookieStr.isBlank()) return false

        return LOGIN_INDICATOR_FIELDS.any { field ->
            cookieStr.contains("$field=")
        }
    }

    /**
     * 检查 Cookie 是否即将过期。
     * 返回 true = 需要刷新。
     */
    fun isExpiringSoon(cookie: String? = null): Boolean {
        val cookieStr = cookie ?: getCookie()
        if (cookieStr.isBlank()) return true

        // 尝试从 cookie 中解析 expiration
        // 注意：CookieManager 不暴露 expiration 时间，只能通过年龄推断
        // 这里简化处理：如果缺少关键 Cookie，认为需要刷新
        return !REQUIRED_COOKIE_FIELDS.all { field ->
            cookieStr.contains("$field=")
        }
    }

    /** 清除所有抖音 Cookie */
    fun clearCookies() {
        val cm = CookieManager.getInstance()
        cm.setCookie("https://$DOUYIN_DOMAIN", "dummy=; Max-Age=0; Path=/")
        cm.setCookie("https://$IESDOUYIN_DOMAIN", "dummy=; Max-Age=0; Path=/")
        cm.removeAllCookies(null)
        CookieSyncManager.getInstance()?.sync()
    }

    /**
     * 验证 Cookie 是否完整。
     * 返回完整度评分（0-100）。
     */
    fun getCookieCompleteness(cookie: String? = null): Int {
        val cookieStr = cookie ?: getCookie()
        if (cookieStr.isBlank()) return 0

        val present = REQUIRED_COOKIE_FIELDS.count { field ->
            cookieStr.contains("$field=")
        }
        return (present * 100 / REQUIRED_COOKIE_FIELDS.size)
    }

    /** 获取 Cookie 状态描述 */
    fun getCookieStatus(cookie: String? = null): String {
        val completeness = getCookieCompleteness(cookie)
        when {
            completeness == 0 -> return "未配置"
            completeness < 50 -> return "Cookie 不完整，可能影响解析成功率"
            completeness < 100 -> return "Cookie 部分缺失，建议重新登录"
            else -> return "Cookie 完整"
        }
    }
}
