package com.douyin.downloader.data.remote

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 抖音分享页（iesdouyin reflow）用 webId 前 16 字节作 AES-128-CBC key/iv，
 * 加密页内 xsstoken，作为 iteminfo 接口的 reflow_id。
 */
object DouyinReflowToken {
    private val WEB_ID_ATTR = Regex("""webId\s*=\s*([0-9]{10,})""")
    private val WEB_ID_JSON = Regex(""""webId"\s*:\s*"([0-9]{10,})"""")
    private val XSS_ATTR = Regex("""xsstoken\s*=\s*([a-fA-F0-9]+)""")

    fun extractWebId(html: String): String? {
        WEB_ID_ATTR.find(html)?.groupValues?.get(1)?.let { return it }
        return WEB_ID_JSON.find(html)?.groupValues?.get(1)
    }

    fun extractXssToken(html: String): String? =
        XSS_ATTR.find(html)?.groupValues?.get(1)

    fun encrypt(webId: String, xssToken: String): String {
        require(webId.length >= 16) { "webId too short for AES key" }
        require(xssToken.isNotEmpty()) { "xsstoken empty" }
        val keyBytes = webId.substring(0, 16).toByteArray(StandardCharsets.UTF_8)
        val key = SecretKeySpec(keyBytes, "AES")
        val iv = IvParameterSpec(keyBytes)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val encrypted = cipher.doFinal(xssToken.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }
}
