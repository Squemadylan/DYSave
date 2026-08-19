package com.douyin.downloader.util

/**
 * 部分平台（如微视）CDN 返回 http 明文地址，Android 9+ 默认拦截 cleartext。
 * 能升 https 则升级；下载层再配合 networkSecurityConfig 兜底。
 */
object MediaUrlNormalizer {
    fun preferHttps(url: String): String {
        val trimmed = url.trim()
        if (trimmed.startsWith("http://", ignoreCase = true)) {
            return "https://" + trimmed.removePrefix("http://").removePrefix("HTTP://")
        }
        return trimmed
    }
}
