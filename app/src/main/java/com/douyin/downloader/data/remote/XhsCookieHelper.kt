package com.douyin.downloader.data.remote

import android.webkit.CookieManager

/**
 * 小红书 Web Cookie 读写与登录态判定（仅本地，不上传）。
 */
object XhsCookieHelper {
    const val HOME_URL = "https://creator.xiaohongshu.com/login"
    private val COOKIE_URLS = listOf(
        "https://www.xiaohongshu.com",
        "https://www.xiaohongshu.com/",
        "https://xiaohongshu.com",
        "https://creator.xiaohongshu.com",
    )

    fun readCookie(): String {
        val cm = CookieManager.getInstance()
        for (url in COOKIE_URLS) {
            val c = cm.getCookie(url)
            if (!c.isNullOrBlank()) return c
        }
        return ""
    }

    /**
     * 登录态判定：存在 access-token-creator / customer-sso-sid / galaxy_creator_session_id 任一即视为已登录。
     * 小红书创作服务平台登录后返回这些 Cookie，而非 web_session。
     */
    fun looksLoggedIn(cookie: String?): Boolean {
        if (cookie.isNullOrBlank()) return false
        return cookie.contains("access-token-creator") ||
            cookie.contains("customer-sso-sid") ||
            cookie.contains("galaxy_creator_session_id") ||
            cookie.contains("x-user-id-creator")
    }

    fun clearXhsCookies() {
        val cm = CookieManager.getInstance()
        for (url in COOKIE_URLS) {
            val raw = cm.getCookie(url) ?: continue
            for (part in raw.split(';')) {
                val name = part.substringBefore('=').trim()
                if (name.isEmpty()) continue
                cm.setCookie(url, "$name=; Max-Age=0; Path=/")
            }
        }
        // 再清一遍常见 host
        cm.setCookie(HOME_URL, "web_session=; Max-Age=0; Path=/")
        cm.setCookie(HOME_URL, "a1=; Max-Age=0; Path=/")
        cm.flush()
    }
}
