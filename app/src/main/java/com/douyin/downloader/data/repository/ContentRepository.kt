package com.douyin.downloader.data.repository

import com.douyin.downloader.data.model.ContentInfo
import com.douyin.downloader.data.model.ParseException
import com.douyin.downloader.data.remote.AnimatedVideoResolver
import com.douyin.downloader.data.remote.DouyinApi
import com.douyin.downloader.data.remote.HtmlParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContentRepository @Inject constructor(
    private val api: DouyinApi,
    private val parser: HtmlParser,
    private val animatedResolver: AnimatedVideoResolver,
) {
    suspend fun parseUrl(rawUrl: String): ContentInfo {
        var url = rawUrl.trim()
        val urlMatch = Regex("""https?://\S+""").find(url)
        if (urlMatch != null) {
            url = urlMatch.value
        }

        if (url.isEmpty()) {
            throw ParseException.InvalidUrl("链接不能为空")
        }

        // 2026 之后：v.douyin.com 短链跳转到 www.douyin.com/aweme-share 新模板页，
        // 新模板页没有 _ROUTER_DATA（数据靠 JS 异步加载 + a_bogus 签名），
        // 拿不到。resolveToShareablePage 会尝试从该页抠出 aweme_id，
        // 再回退到 iesdouyin 旧模板（iOS UA 仍能取到 _ROUTER_DATA）。
        url = api.resolveToShareablePage(url)

        val (type, id) = parser.extractIds(url)

        return if (type == "video") {
            fetchVideoInfo(id)
        } else {
            fetchNoteInfo(id)
        }
    }

    private suspend fun fetchVideoInfo(videoId: String): ContentInfo.Video {
        val shareUrl = "https://www.iesdouyin.com/share/video/$videoId/"
        val html = api.fetchPage(shareUrl)
        val routerData = parser.extractRouterData(html)
        return parser.parseVideoInfo(routerData, videoId)
    }

    private suspend fun fetchNoteInfo(noteId: String): ContentInfo {
        val shareUrl = "https://www.iesdouyin.com/share/note/$noteId/"
        val html = api.fetchPage(shareUrl)
        val routerData = parser.extractRouterData(html)
        val noteData = parser.parseNoteInfo(routerData, noteId)

        val isAnimated = noteData.images.size == 1

        if (isAnimated) {
            var videoUrl = ""
            try {
                videoUrl = animatedResolver.resolve(noteId)
            } catch (e: Exception) {
                throw ParseException.AnimatedVideoResolveFailed("动图视频地址解析失败：${e.message ?: "未知错误"}")
            }

            if (videoUrl.isEmpty()) {
                videoUrl = parser.findDouyinvodUrl(routerData)
                if (videoUrl.isEmpty()) {
                    throw ParseException.AnimatedVideoResolveFailed("未找到动图视频地址，请确认帖子为公开内容")
                }
            }

            return ContentInfo.Animated(
                id = noteData.noteId,
                title = noteData.title,
                author = noteData.author,
                cover = noteData.cover,
                images = noteData.images,
                musicUrl = noteData.musicUrl,
                duration = noteData.duration,
                videoUrl = videoUrl,
                qualities = noteData.qualities,
            )
        }

        return ContentInfo.ImageGallery(
            id = noteData.noteId,
            title = noteData.title,
            author = noteData.author,
            cover = noteData.cover,
            images = noteData.images,
            musicUrl = noteData.musicUrl,
            duration = noteData.duration,
        )
    }
}