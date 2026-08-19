package com.douyin.downloader.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 进程内按 host 记住 Cookie。
 * - 抖音本地 iteminfo 需要分享页下发的 ttwid 等，OkHttp 自动存入；
 * - 同时支持手动注入用户 Cookie（[saveFromResponse] 复用同一合并逻辑），
 *   让 DYSave 把「填好的抖音 Cookie」带上去，缓解 WAF / 限流。
 */
@Singleton
class MemoryCookieJar @Inject constructor() : CookieJar {
    private val store = ConcurrentHashMap<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.topPrivateDomain() ?: url.host
        val merged = (store[host].orEmpty() + cookies)
            .groupBy { it.name + "|" + it.domain }
            .mapValues { (_, v) -> v.last() }
            .values
            .toList()
        store[host] = merged
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.topPrivateDomain() ?: url.host
        val now = System.currentTimeMillis()
        val cookies = store[host].orEmpty().filter { it.expiresAt >= now }
        store[host] = cookies
        return cookies.filter { it.matches(url) }
    }

    /** 清空指定顶级域下所有已存 Cookie（用于用户退出登录 / 清空 Cookie）。 */
    fun clearDomain(domain: String) {
        store.remove(domain)
    }
}
