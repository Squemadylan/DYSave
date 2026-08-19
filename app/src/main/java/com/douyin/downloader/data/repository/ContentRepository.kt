package com.douyin.downloader.data.repository

import com.douyin.downloader.data.local.SettingsRepository
import com.douyin.downloader.data.model.ContentInfo
import com.douyin.downloader.data.model.LinkPlatform
import com.douyin.downloader.data.model.ParseException
import com.douyin.downloader.data.model.VideoQuality
import com.douyin.downloader.data.remote.AnimatedVideoResolver
import com.douyin.downloader.data.remote.DouyinApi
import com.douyin.downloader.data.remote.FiftyTwoApiClient
import com.douyin.downloader.data.remote.HtmlParser
import com.douyin.downloader.data.remote.LinkPlatformDetector
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class ContentRepository @Inject constructor(
    private val api: DouyinApi,
    private val parser: HtmlParser,
    private val animatedResolver: AnimatedVideoResolver,
    private val fiftyTwoApi: FiftyTwoApiClient,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun parseUrl(rawUrl: String): ContentInfo {
        val extracted = LinkPlatformDetector.extractUrl(rawUrl)?.trim().orEmpty()
        if (extracted.isEmpty() && rawUrl.trim().isEmpty()) {
            throw ParseException.InvalidUrl("链接不能为空")
        }
        val url = extracted.ifEmpty { rawUrl.trim() }
        val platform = LinkPlatformDetector.detect(url)
        return when (platform) {
            LinkPlatform.DOUYIN -> parseDouyinWithFallback(url)
            LinkPlatform.SHIPINHAO,
            LinkPlatform.HAOKAN,
            LinkPlatform.WEISHI -> fiftyTwoApi.parse(platform, url)
            LinkPlatform.XIAOHONGSHU -> {
                val cookie = settingsRepository.flow.first().xhsCookie
                fiftyTwoApi.parse(platform, url, xhsCookieRaw = cookie)
            }
            LinkPlatform.UNKNOWN -> throw ParseException.UnsupportedPlatform()
        }
    }

    /**
     * 抖音优先本地（SSR → iteminfo）；仍失败再回退 52API。
     */
    private suspend fun parseDouyinWithFallback(url: String): ContentInfo {
        try {
            return parseDouyinLocal(url)
        } catch (localError: Exception) {
            android.util.Log.w(
                "ContentRepository",
                "抖音本地解析失败，回退 52API: ${localError.message}",
            )
            return try {
                fiftyTwoApi.parse(LinkPlatform.DOUYIN, url).withCloudIfVideo()
            } catch (apiError: Exception) {
                // 保留本地错误更贴近用户粘贴场景；若 API 有明确文案则优先
                throw when (apiError) {
                    is ParseException -> apiError
                    else -> localError
                }
            }
        }
    }

    suspend fun resolveDouyinCloudParse(rawUrl: String): String {
        val url = LinkPlatformDetector.extractUrl(rawUrl) ?: rawUrl.trim()
        val info = fiftyTwoApi.parse(LinkPlatform.DOUYIN, url)
        return when (info) {
            is ContentInfo.Video -> info.videoUrl
            is ContentInfo.Animated -> info.videoUrl
            else -> throw ParseException.VideoUrlNotFound("云解析未返回视频地址")
        }
    }

    private suspend fun parseDouyinLocal(url: String): ContentInfo {
        // 短链 → aweme_id → ies 分享页；SSR 有 item_list 直接用，
        // 否则用页内 webId/xsstoken 调 iteminfo（本地）。
        val resolvedUrl = api.resolveToShareablePage(url)

        val (type, id) = parser.extractIds(resolvedUrl)

        return if (type == "video") {
            fetchVideoInfo(id)
        } else {
            fetchNoteInfo(id)
        }
    }

    private suspend fun fetchVideoInfo(videoId: String): ContentInfo.Video {
        val shareUrl = "https://www.iesdouyin.com/share/video/$videoId/"
        val html = api.fetchPage(shareUrl)
        // 1) 旧 SSR：_ROUTER_DATA 内嵌 videoInfoRes
        try {
            val routerData = parser.extractRouterData(html)
            if (parser.hasPlayableItem(routerData)) {
                return parser.parseVideoInfo(routerData, videoId).withCloud()
            }
        } catch (e: Exception) {
            android.util.Log.d("ContentRepository", "SSR 解析跳过: ${e.message}")
        }
        // 2) 新本地：iteminfo + AES(reflow_id)
        val itemInfo = api.fetchItemInfo(videoId, html)
        return when (val info = parser.parseItemInfo(itemInfo, videoId)) {
            is ContentInfo.Video -> info.withCloud()
            else -> throw ParseException.VideoUrlNotFound("iteminfo 未返回视频地址")
        }
    }

    private suspend fun fetchNoteInfo(noteId: String): ContentInfo {
        val shareUrl = "https://www.iesdouyin.com/share/note/$noteId/"
        val html = api.fetchPage(shareUrl)

        var routerDataForVod = ""
        val noteData = try {
            val routerData = parser.extractRouterData(html)
            routerDataForVod = routerData
            if (parser.hasPlayableItem(routerData)) {
                parser.parseNoteInfo(routerData, noteId)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } ?: run {
            // note 模板空壳时，iteminfo 同样可拉图文/视频
            val itemInfo = api.fetchItemInfo(noteId, html)
            when (val info = parser.parseItemInfo(itemInfo, noteId)) {
                is ContentInfo.ImageGallery -> HtmlParser.NoteRawData(
                    noteId = info.id,
                    title = info.title,
                    author = info.author,
                    cover = info.cover,
                    images = info.images,
                    musicUrl = info.musicUrl,
                    duration = info.duration,
                )
                is ContentInfo.Video -> {
                    return info.withCloud()
                }
                else -> throw ParseException.ParseFailed("未找到帖子数据，页面结构可能已变化")
            }
        }

        val isAnimated = noteData.images.size == 1

        if (isAnimated) {
            var videoUrl = ""
            try {
                videoUrl = animatedResolver.resolve(noteId)
            } catch (e: Exception) {
                throw ParseException.AnimatedVideoResolveFailed("动图视频地址解析失败：${e.message ?: "未知错误"}")
            }

            if (videoUrl.isEmpty()) {
                videoUrl = parser.findDouyinvodUrl(routerDataForVod)
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
            ).withCloud()
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

    private fun withCloudPlaceholder(qualities: List<VideoQuality>): List<VideoQuality> =
        qualities + VideoQuality(label = "云解析", bitRate = -1, url = "", isCloudParse = true)

    private fun ContentInfo.Video.withCloud(): ContentInfo.Video =
        copy(qualities = withCloudPlaceholder(qualities))

    private fun ContentInfo.Animated.withCloud(): ContentInfo.Animated =
        copy(qualities = withCloudPlaceholder(qualities))

    private fun ContentInfo.withCloudIfVideo(): ContentInfo = when (this) {
        is ContentInfo.Video -> withCloud()
        is ContentInfo.Animated -> withCloud()
        else -> this
    }
}
