package com.douyin.downloader.data.remote

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.HttpUrl

/**
 * 抖音 Web Cookie 读写与登录态判定（仅本地，不上传）。
 * 镜像 [XhsCookieHelper]，供「我的」页填入抖音 Cookie，
 * 由 [DouyinApi] 注入到请求，缓解 WAF / 限流。
 */
object DouyinCookieHelper {
    const val HOME_URL = "https://www.douyin.com/passport/sso/"
    private val COOKIE_URLS = listOf(
        "https://www.douyin.com",
        "https://www.douyin.com/",
        "https://douyin.com",
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
     * 登录态判定：存在 sessionid_ss / sid_guard / sid_tt 任一即视为已登录
     * （这些是抖音 Web 登录后下发的会话 Cookie）。
     */
    fun looksLoggedIn(cookie: String?): Boolean {
        if (cookie.isNullOrBlank()) return false
        return cookie.contains("sessionid_ss=") ||
            cookie.contains("sid_guard=") ||
            cookie.contains("sid_tt=")
    }

    /**
     * 把用户粘贴 / 登录的 Cookie 原文解析成 OkHttp Cookie。
     * 同时注入 douyin.com 与 iesdouyin.com 两个域，覆盖解析路径
     * （www.douyin.com 跳转页 + www.iesdouyin.com 的 iteminfo）。
     * 设一年过期，避免被 MemoryCookieJar 当成会话 Cookie 丢弃。
     */
    fun parseToCookies(raw: String): List<Cookie> {
        val expiry = System.currentTimeMillis() + 365L * 24 * 3600 * 1000
        val out = mutableListOf<Cookie>()
        raw.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && '=' in it }
            .forEach { pair ->
                val eq = pair.indexOf('=')
                val name = pair.substring(0, eq).trim()
                val value = pair.substring(eq + 1).trim()
                if (name.isEmpty()) return@forEach
                for (domain in listOf("douyin.com", "iesdouyin.com")) {
                    out += Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(domain)
                        .path("/")
                        .expiresAt(expiry)
                        .secure()
                        .build()
                }
            }
        return out
    }

    fun clearDouyinCookies() {
        val cm = CookieManager.getInstance()
        for (url in COOKIE_URLS) {
            val raw = cm.getCookie(url) ?: continue
            for (part in raw.split(';')) {
                val name = part.substringBefore('=').trim()
                if (name.isEmpty()) continue
                cm.setCookie(url, "$name=; Max-Age=0; Path=/")
            }
        }
        cm.setCookie(HOME_URL, "sessionid_ss=; Max-Age=0; Path=/")
        cm.flush()
    }
}

/** 构造一个用于 [MemoryCookieJar.saveFromResponse] 的占位 URL。 */
fun douyinSeedUrl(host: String): HttpUrl =
    HttpUrl.Builder().scheme("https").host(host).build()
