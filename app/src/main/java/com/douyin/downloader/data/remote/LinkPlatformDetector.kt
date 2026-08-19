package com.douyin.downloader.data.remote

import com.douyin.downloader.data.model.LinkPlatform
import java.util.Locale

object LinkPlatformDetector {
    private val URL_REGEX = Regex("""https?://[A-Za-z0-9\-._~:/?#@!$&'()*+,;=%]+""")

    fun extractUrl(raw: String): String? {
        val m = URL_REGEX.find(raw.trim()) ?: return null
        var url = m.value.trimEnd(
            '，', '。', ',', '.', ';', '；', ':', '：',
            '!', '！', '?', '？', '、', ')', '）', ']', '】',
            '"', '\'', '”', '“', '’', '‘',
        )
        // 分享文案里短链后常跟 " reB:/" 等 token；若误吞到空白后内容，截到首个空白
        val space = url.indexOfFirst { it.isWhitespace() }
        if (space >= 0) url = url.substring(0, space)
        return url.takeIf { it.isNotBlank() }
    }

    fun detect(rawOrUrl: String): LinkPlatform {
        val url = (extractUrl(rawOrUrl) ?: rawOrUrl).lowercase(Locale.ROOT)
        return when {
            "douyin.com" in url -> LinkPlatform.DOUYIN
            "channels.weixin.qq.com" in url ||
                ("weixin.qq.com" in url && "/sph" in url) -> LinkPlatform.SHIPINHAO
            "haokan.baidu.com" in url || "haokan.hao123.com" in url -> LinkPlatform.HAOKAN
            "video.weishi.qq.com" in url || "weishi.qq.com" in url ||
                "isee.weishi.qq.com" in url -> LinkPlatform.WEISHI
            "xiaohongshu.com" in url || "xhslink.com" in url || "xhslink.cn" in url ||
                Regex("""(^|//)xhs\.cn([/?#]|$)""").containsMatchIn(url) -> LinkPlatform.XIAOHONGSHU
            else -> LinkPlatform.UNKNOWN
        }
    }
}
