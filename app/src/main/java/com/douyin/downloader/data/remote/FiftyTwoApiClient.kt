package com.douyin.downloader.data.remote

import android.util.Base64
import com.douyin.downloader.data.model.ContentInfo
import com.douyin.downloader.data.model.LinkPlatform
import com.douyin.downloader.data.model.ParseException
import com.douyin.downloader.data.model.VideoQuality
import com.douyin.downloader.util.MediaUrlNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FiftyTwoApiClient @Inject constructor(
    private val client: OkHttpClient,
) {
    companion object {
        /**
         * 各平台字段不统一：
         * - 抖音: work_title / work_author / work_url
         * - 视频号: video_title / video_author / video_url
         * - 好看: video_title / video_authorName / video_playUrl[{title,url}]
         * - 快手等同系: work_authorName 等
         */
        private fun firstString(data: JSONObject, vararg keys: String): String {
            for (key in keys) {
                val v = data.optString(key)
                if (v.isNotBlank()) return v
            }
            return ""
        }

        private fun firstRaw(data: JSONObject, vararg keys: String): Any? {
            for (key in keys) {
                if (!data.has(key) || data.isNull(key)) continue
                return data.opt(key)
            }
            return null
        }

        /** 从好看式 play 列表提取清晰度；优先超清(sc)→高清(hd)→标清(sd)。 */
        private fun qualitiesFromPlayList(arr: JSONArray): List<VideoQuality> {
            val items = mutableListOf<Triple<String, String, Int>>() // label, url, rank
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val url = firstString(obj, "url", "play_url", "video_url")
                if (url.isBlank()) continue
                val key = obj.optString("key").lowercase()
                val label = obj.optString("title").ifBlank {
                    when (key) {
                        "sc" -> "超清"
                        "hd" -> "高清"
                        "sd" -> "标清"
                        else -> key.ifBlank { "清晰度${i + 1}" }
                    }
                }
                val rank = when (key) {
                    "sc" -> 3
                    "hd" -> 2
                    "sd" -> 1
                    else -> 0
                }
                items.add(Triple(label, url, rank))
            }
            return items
                .sortedByDescending { it.third }
                .map { (label, url, rank) ->
                    VideoQuality(label = label, bitRate = rank, url = url)
                }
        }

        private fun stringListFromArray(arr: JSONArray): List<String> {
            val out = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                when (val item = arr.opt(i)) {
                    is String -> if (item.isNotBlank()) out.add(item)
                    is JSONObject -> {
                        val u = firstString(item, "url", "image", "pic", "src")
                        if (u.isNotBlank()) out.add(u)
                    }
                }
            }
            return out
        }

        internal fun mapDataToContentInfo(data: JSONObject, sourceUrl: String): ContentInfo {
            val title = firstString(
                data, "work_title", "video_title", "title",
            ).ifBlank { "未命名" }
            val author = firstString(
                data,
                "work_author", "video_author", "video_authorName", "work_authorName", "author",
            ).ifBlank { "未知作者" }
            val cover = firstString(
                data, "work_cover", "video_cover", "cover", "cover_url", "img_url",
            )
            val type = firstString(data, "work_type", "video_type", "type").lowercase()
            val id = Integer.toHexString(sourceUrl.hashCode())
            val musicUrl = firstString(
                data,
                "music_url", "work_musicBgm",
            ).ifBlank {
                data.optJSONObject("music")?.optString("url").orEmpty()
            }

            val imageList = mutableListOf<String>()
            var videoUrl = ""
            var qualities = emptyList<VideoQuality>()

            // 1) 好看: video_playUrl 对象数组
            val playList = firstRaw(data, "video_playUrl", "play_list", "play_url_list")
            if (playList is JSONArray && playList.length() > 0 && playList.optJSONObject(0) != null) {
                qualities = qualitiesFromPlayList(playList)
                videoUrl = qualities.firstOrNull()?.url.orEmpty()
            }

            // 2) 主资源字段：字符串或图片/清晰度数组
            if (videoUrl.isBlank()) {
                val media = firstRaw(
                    data,
                    "work_url", "video_url", "url", "play_url", "video", "downurl",
                )
                when (media) {
                    is JSONArray -> {
                        val firstObj = media.optJSONObject(0)
                        val looksLikePlayObjects = firstObj != null &&
                            firstString(firstObj, "url", "play_url").isNotBlank()
                        if (looksLikePlayObjects) {
                            qualities = qualitiesFromPlayList(media)
                            videoUrl = qualities.firstOrNull()?.url.orEmpty()
                        } else {
                            imageList.addAll(stringListFromArray(media))
                        }
                    }
                    is String -> {
                        if (type.contains("image") || type.contains("note") || type.contains("gallery")) {
                            if (media.isNotBlank()) imageList.add(media)
                        } else {
                            videoUrl = media
                        }
                    }
                }
            }

            // 3) 显式图集字段
            if (imageList.isEmpty()) {
                val imagesRaw = firstRaw(data, "images", "image_list", "pics", "pic_list", "img_list")
                if (imagesRaw is JSONArray) {
                    imageList.addAll(stringListFromArray(imagesRaw))
                } else if (imagesRaw is String && imagesRaw.isNotBlank()) {
                    imageList.add(imagesRaw)
                }
            }

            if (imageList.isNotEmpty() && videoUrl.isBlank()) {
                return ContentInfo.ImageGallery(
                    id = id,
                    title = title,
                    author = author,
                    cover = cover.ifBlank { imageList.first() },
                    images = imageList,
                    musicUrl = musicUrl,
                    duration = 0,
                )
            }
            if (videoUrl.isBlank()) {
                throw ParseException.VideoUrlNotFound("未找到可下载的视频地址")
            }
            videoUrl = MediaUrlNormalizer.preferHttps(videoUrl)
            val finalQualities = qualities.ifEmpty {
                listOf(VideoQuality(label = "默认", bitRate = 0, url = videoUrl))
            }.map { q -> q.copy(url = MediaUrlNormalizer.preferHttps(q.url)) }
            return ContentInfo.Video(
                id = id,
                title = title,
                author = author,
                cover = MediaUrlNormalizer.preferHttps(cover).ifBlank { cover },
                videoUrl = videoUrl,
                qualities = finalQualities,
            )
        }
    }

    suspend fun parse(platform: LinkPlatform, url: String, xhsCookieRaw: String = ""): ContentInfo =
        withContext(Dispatchers.IO) {
            val path = when (platform) {
                LinkPlatform.DOUYIN -> "douyin"
                LinkPlatform.SHIPINHAO -> "sph"
                LinkPlatform.HAOKAN -> "haokan"
                LinkPlatform.WEISHI -> "weishi"
                LinkPlatform.XIAOHONGSHU -> "xhs"
                LinkPlatform.UNKNOWN -> throw ParseException.UnsupportedPlatform()
            }
            if (platform == LinkPlatform.XIAOHONGSHU && xhsCookieRaw.isBlank()) {
                throw ParseException.XhsCookieRequired()
            }
            val httpUrl = HttpUrl.Builder()
                .scheme("https").host("www.52api.cn")
                .addPathSegment("api").addPathSegment(path)
                .addQueryParameter("key", FiftyTwoApiKey.value)
                .addQueryParameter("url", url)
                .apply {
                    if (platform == LinkPlatform.XIAOHONGSHU) {
                        val b64 = Base64.encodeToString(
                            xhsCookieRaw.toByteArray(Charsets.UTF_8),
                            Base64.NO_WRAP,
                        )
                        addQueryParameter("cookie", b64)
                    }
                }.build()
            val request = Request.Builder().url(httpUrl).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw ParseException.ApiFailed("解析服务异常，HTTP ${resp.code}")
                }
                val root = JSONObject(body)
                val code = root.optInt("code", -1)
                if (code != 200) {
                    val msg = root.optString("msg").ifBlank { "解析失败" }
                    throw ParseException.ApiFailed(msg)
                }
                val data = root.optJSONObject("data")
                    ?: throw ParseException.ApiFailed("返回数据为空")
                mapDataToContentInfo(data, url)
            }
        }
}
