package com.douyin.downloader.data.remote

import com.douyin.downloader.data.model.ParseException
import com.douyin.downloader.data.model.ContentInfo
import com.douyin.downloader.data.model.VideoQuality
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HtmlParser @Inject constructor() {

    fun extractIds(url: String): Pair<String, String> {
        Regex("""/(?:share/)?video/(\d+)""").find(url)?.let {
            return "video" to it.groupValues[1]
        }
        Regex("""/(?:share/)?note/(\d+)""").find(url)?.let {
            return "note" to it.groupValues[1]
        }
        Regex("""item_ids=(\d+)""").find(url)?.let {
            return "video" to it.groupValues[1]
        }
        throw ParseException.InvalidUrl()
    }

    /**
     * 从 iesdouyin HTML 中提取 window._ROUTER_DATA = {...} 的 JSON 字符串
     */
    fun extractRouterData(html: String): String {
        val marker = "window._ROUTER_DATA"
        val startIdx = html.indexOf(marker)
        if (startIdx < 0) throw ParseException.ParseFailed("无法解析页面数据，页面缺少 _ROUTER_DATA")

        val eqIdx = html.indexOf("=", startIdx)
        if (eqIdx < 0) throw ParseException.ParseFailed("无法解析页面数据，等号位置无效")

        val braceStart = html.indexOf("{", eqIdx)
        if (braceStart < 0) throw ParseException.ParseFailed("无法解析页面数据，JSON 起始位置未找到")

        var depth = 0
        for (i in braceStart until html.length) {
            when (html[i]) {
                '{' -> depth++
                '}' -> depth--
            }
            if (depth == 0) {
                val rawJson = html.substring(braceStart, i + 1)
                return decodeUnicodeEscapes(rawJson)
            }
        }
        throw ParseException.ParseFailed("无法解析页面数据，JSON 括号不匹配")
    }

    /**
     * 解析视频信息。优先使用 JSON 解析，回退到正则。
     */
    fun parseVideoInfo(routerData: String, videoId: String): ContentInfo.Video {
        val videoItem = findItemList(routerData)
        if (videoItem != null) {
            val videoObj = videoItem.optJSONObject("video")
            val qualities = extractQualities(videoObj)
            val playUrl = qualities.firstOrNull()?.url
                ?: videoObj?.optJSONObject("play_addr")
                    ?.optJSONArray("url_list")
                    ?.optString(0, "")
                    ?.let { it.replace("playwm", "play") }
                ?: ""

            if (playUrl.isEmpty()) throw ParseException.VideoUrlNotFound()

            val cover = videoObj?.optJSONObject("cover")
                ?.optJSONArray("url_list")
                ?.optString(0, "")
                ?: videoObj?.optJSONObject("origin_cover")
                    ?.optJSONArray("url_list")
                    ?.optString(0, "")
                ?: ""

            return ContentInfo.Video(
                id = videoId,
                title = decodeText(videoItem.optString("desc", "")),
                author = videoItem.optJSONObject("author")?.optString("nickname", "")?.let { decodeText(it) } ?: "",
                cover = cover,
                videoUrl = playUrl,
                qualities = qualities,
            )
        }

        // 正则回退
        val playMatch = Regex(""""(https?://[^"\\]*playwm[^"\\]*)"""").find(routerData)
            ?: throw ParseException.VideoUrlNotFound()
        return ContentInfo.Video(
            id = videoId,
            title = extractFieldFallback(routerData, "desc"),
            author = extractFieldFallback(routerData, "nickname"),
            cover = extractCoverFallback(routerData),
            videoUrl = playMatch.groupValues[1].replace("playwm", "play"),
        )
    }

    /**
     * 从 video 对象中提取清晰度列表。
     * 抖音的 _ROUTER_DATA 中:
     *   - bit_rate 数组：每项含 bit_rate 数值、play_addr.url_list[0]、is_h265
     *   - play_addr.url_list[0]：默认清晰度（无水印替换前的 playwm 形式）
     */
    private fun extractQualities(videoObj: JSONObject?): List<VideoQuality> {
        if (videoObj == null) return emptyList()
        val result = mutableListOf<VideoQuality>()

        // 1) 优先从 bit_rate 数组提取（包含多个清晰度）
        val bitRates = videoObj.optJSONArray("bit_rate")
        if (bitRates != null && bitRates.length() > 0) {
            val seen = mutableSetOf<String>()
            for (i in 0 until bitRates.length()) {
                val br = bitRates.optJSONObject(i) ?: continue
                val playAddr = br.optJSONObject("play_addr") ?: continue
                val urlList = playAddr.optJSONArray("url_list") ?: continue
                val rawUrl = urlList.optString(0, "")
                if (rawUrl.isEmpty()) continue
                val finalUrl = rawUrl.replace("playwm", "play")
                if (!seen.add(finalUrl)) continue

                val bitRateValue = br.optInt("bit_rate", 0)
                val isH265 = br.optInt("is_h265", 0) == 1 ||
                    br.optString("gear_name", "").contains("h265", ignoreCase = true)
                val gearName = br.optString("gear_name", "")
                val label = qualityLabel(bitRateValue, gearName, isH265, videoObj)
                result.add(
                    VideoQuality(
                        label = label,
                        bitRate = bitRateValue,
                        url = finalUrl,
                        format = "mp4",
                        isH265 = isH265,
                    )
                )
            }
        }

        // 2) 如果 bit_rate 为空/没有结果，使用顶层 play_addr
        if (result.isEmpty()) {
            val playAddr = videoObj.optJSONObject("play_addr")
            val urlList = playAddr?.optJSONArray("url_list")
            val url = urlList?.optString(0, "")?.replace("playwm", "play").orEmpty()
            if (url.isNotEmpty()) {
                result.add(
                    VideoQuality(
                        label = qualityLabel(0, "", false, videoObj),
                        bitRate = 0,
                        url = url,
                    )
                )
            }
        }

        // 按 bit_rate 降序（高清晰度在前）
        return result.sortedByDescending { it.bitRate }
    }

    private fun qualityLabel(
        bitRate: Int,
        gearName: String,
        isH265: Boolean,
        videoObj: JSONObject,
    ): String {
        // 优先使用抖音的 gear_name（中文友好：超清、高清、标清）
        if (gearName.isNotEmpty()) return gearName
        // 其次根据 bit_rate 推断
        if (bitRate > 4_000_000) return "超清"
        if (bitRate > 2_000_000) return "高清"
        if (bitRate > 1_000_000) return "标清"
        // 最后用 video.play_addr 的清晰度标识
        val dataSize = videoObj.optInt("video_size", 0)
        if (dataSize > 0) {
            val mb = dataSize.toDouble() / (1024 * 1024)
            if (mb > 50) return "高清"
            if (mb > 20) return "标清"
        }
        return if (isH265) "高清 H.265" else "标清"
    }

    /**
     * 解析图文/动图笔记信息。
     */
    fun parseNoteInfo(routerData: String, noteId: String): NoteRawData {
        val noteItem = findItemList(routerData)
            ?: throw ParseException.ParseFailed("未找到帖子数据，页面结构可能已变化")

        val images = extractImagesFromJson(noteItem)
        if (images.isEmpty()) throw ParseException.ParseFailed("未找到图片数据，该帖子可能已被删除或不可见")

        val music = noteItem.optJSONObject("music")
        val musicUrl = music?.optString("play_url", null)
            ?.takeIf { it.isNotEmpty() }
            ?.let { urlListFirst(it) }
            ?: music?.optJSONObject("play_url")
                ?.optJSONArray("url_list")
                ?.optString(0, "")
            ?: ""

        val duration = noteItem.optInt("duration", 0)
        val cover = noteItem.optJSONObject("video")
            ?.optJSONObject("cover")
            ?.optJSONArray("url_list")
            ?.optString(0, "")
            ?: images.first()

        val videoObj = noteItem.optJSONObject("video")
        val qualities = videoObj?.let { extractQualities(it) } ?: emptyList()

        return NoteRawData(
            noteId = noteId,
            title = decodeText(noteItem.optString("desc", "")),
            author = noteItem.optJSONObject("author")?.optString("nickname", "")?.let { decodeText(it) } ?: "",
            cover = cover,
            images = images,
            musicUrl = musicUrl,
            duration = duration,
            qualities = qualities,
        )
    }

    fun findDouyinvodUrl(text: String): String {
        val match = Regex(""""(https?://[^"\\]*douyinvod[^"\\]*)"""").find(text)
        return match?.groupValues?.get(1) ?: ""
    }

    /**
     * 在 _ROUTER_DATA JSON 中查找 item_list 数组。
     */
    private fun findItemList(routerData: String): JSONObject? {
        return try {
            val root = JSONObject(routerData)
            // 优先：loaderData.video_(id)/page 或 note_(id)/page
            val loaderData = root.optJSONObject("loaderData")
            val page = loaderData?.let { ld ->
                ld.keys().asSequence()
                    .firstOrNull { it.startsWith("video_") || it.startsWith("note_") }
                    ?.let { ld.optJSONObject(it) }
            }
            val itemList = page
                ?.optJSONObject("videoInfoRes")
                ?.optJSONArray("item_list")
                ?: findItemListDeep(root, depth = 0)
            itemList?.optJSONObject(0)
        } catch (_: Exception) {
            null
        }
    }

    private fun findItemListDeep(obj: Any?, depth: Int): JSONArray? {
        if (depth > 10) return null
        when (obj) {
            is JSONObject -> {
                if (obj.has("item_list") && obj.opt("item_list") is JSONArray) {
                    return obj.optJSONArray("item_list")
                }
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val r = findItemListDeep(obj.opt(keys.next()), depth + 1)
                    if (r != null) return r
                }
            }
            is JSONArray -> {
                for (i in 0 until obj.length()) {
                    val r = findItemListDeep(obj.opt(i), depth + 1)
                    if (r != null) return r
                }
            }
        }
        return null
    }

    private fun extractImagesFromJson(item: JSONObject): List<String> {
        val images = item.optJSONArray("images") ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until images.length()) {
            val urlList = images.optJSONObject(i)
                ?.optJSONArray("url_list")
                ?: continue
            val first = urlList.optString(0, "")
            if (first.isNotEmpty()) result.add(first)
        }
        return result
    }

    private fun urlListFirst(playUrlJson: String): String {
        return try {
            JSONObject(playUrlJson).optJSONArray("url_list")?.optString(0, "") ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractFieldFallback(data: String, field: String): String {
        val pattern = """\\"$field\\"\s*:\s*\\"(.*?)\\"""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val match = pattern.find(data)
        return match?.groupValues?.get(1)?.let { decodeText(it) } ?: ""
    }

    private fun extractCoverFallback(data: String): String {
        val match = Regex(""""\\"cover\\".*?\\"url_list\\"\s*:\s*\[\s*\\"(.*?)\\"""").find(data)
        return match?.groupValues?.get(1) ?: ""
    }

    fun decodeDouyinText(text: String): String = decodeText(text)

    private fun decodeText(text: String): String {
        if (text.isEmpty()) return text
        val unicodeDecoded = decodeUnicodeEscapes(text)
        return try {
            URLDecoder.decode(unicodeDecoded, "UTF-8")
        } catch (_: Exception) {
            unicodeDecoded
        }
    }

    private fun decodeUnicodeEscapes(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (i + 5 < text.length && text[i] == '\\' && text[i + 1] == 'u') {
                val hex = text.substring(i + 2, i + 6)
                try {
                    val codePoint = hex.toInt(16)
                    sb.append(codePoint.toChar())
                    i += 6
                    continue
                } catch (_: NumberFormatException) { }
            }
            sb.append(text[i])
            i++
        }
        return sb.toString()
    }

    data class NoteRawData(
        val noteId: String,
        val title: String,
        val author: String,
        val cover: String,
        val images: List<String>,
        val musicUrl: String,
        val duration: Int,
        val qualities: List<VideoQuality> = emptyList(),
    )
}