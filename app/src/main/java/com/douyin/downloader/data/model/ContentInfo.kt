package com.douyin.downloader.data.model

data class VideoQuality(
    val label: String,           // "1080p"、"720p"、"540p" 等展示标签
    val bitRate: Int,            // 原始 bit_rate 数值（用于排序/调试）
    val url: String,             // 视频下载 URL
    val format: String = "mp4",  // 容器格式
    val isH265: Boolean = false, // 是否 H.265
)

sealed class ContentInfo {
    abstract val id: String
    abstract val title: String
    abstract val author: String
    abstract val cover: String

    data class Video(
        override val id: String,
        override val title: String,
        override val author: String,
        override val cover: String,
        val videoUrl: String,
        val qualities: List<VideoQuality> = emptyList(),
    ) : ContentInfo()

    data class ImageGallery(
        override val id: String,
        override val title: String,
        override val author: String,
        override val cover: String,
        val images: List<String>,
        val musicUrl: String,
        val duration: Int,
    ) : ContentInfo()

    data class Animated(
        override val id: String,
        override val title: String,
        override val author: String,
        override val cover: String,
        val images: List<String>,
        val musicUrl: String,
        val duration: Int,
        val videoUrl: String,
        val qualities: List<VideoQuality> = emptyList(),
    ) : ContentInfo()
}
