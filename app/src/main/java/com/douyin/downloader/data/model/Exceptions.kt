package com.douyin.downloader.data.model

sealed class ParseException(message: String, val code: String) : Exception(message) {
    companion object {
        const val INVALID_URL = "INVALID_URL"
        const val URL_RESOLVE_FAILED = "URL_RESOLVE_FAILED"
        const val PAGE_FETCH_FAILED = "PAGE_FETCH_FAILED"
        const val PARSE_FAILED = "PARSE_FAILED"
        const val UNSUPPORTED_TYPE = "UNSUPPORTED_TYPE"
        const val VIDEO_URL_NOT_FOUND = "VIDEO_URL_NOT_FOUND"
        const val ANIMATED_VIDEO_RESOLVE_FAILED = "ANIMATED_VIDEO_RESOLVE_FAILED"
        const val NETWORK_ERROR = "NETWORK_ERROR"
    }

    class InvalidUrl(message: String = "链接格式无效，请检查是否是正确的抖音链接") : ParseException(message, INVALID_URL)
    class UrlResolveFailed(message: String = "短链接跳转失败，请检查网络或链接有效性") : ParseException(message, URL_RESOLVE_FAILED)
    class PageFetchFailed(message: String = "页面加载失败，请检查网络连接或链接是否已失效") : ParseException(message, PAGE_FETCH_FAILED)
    class ParseFailed(message: String = "页面数据解析失败，抖音页面结构可能已更新") : ParseException(message, PARSE_FAILED)
    class UnsupportedType(message: String = "无法识别该内容类型") : ParseException(message, UNSUPPORTED_TYPE)
    class VideoUrlNotFound(message: String = "未找到无水印视频地址，请确认视频为公开视频") : ParseException(message, VIDEO_URL_NOT_FOUND)
    class AnimatedVideoResolveFailed(message: String = "动图视频地址解析失败，将使用图片模式下载") : ParseException(message, ANIMATED_VIDEO_RESOLVE_FAILED)
    class NetworkError(message: String = "网络错误，请检查网络连接") : ParseException(message, NETWORK_ERROR)
}

sealed class DownloadException(message: String, val code: String) : Exception(message) {
    companion object {
        const val FILE_WRITE_FAILED = "FILE_WRITE_FAILED"
        const val DOWNLOAD_FAILED = "DOWNLOAD_FAILED"
        const val FFMPEG_FAILED = "FFmpeg合成失败"
        const val MEDIA_STORE_ERROR = "保存到相册失败"
    }

    class FileWriteFailed(message: String = "文件写入失败，请检查存储空间是否充足") : DownloadException(message, FILE_WRITE_FAILED)
    class DownloadFailed(message: String = "下载失败，请检查网络或链接有效性") : DownloadException(message, DOWNLOAD_FAILED)
    class FFmpegFailed(message: String = "视频合成失败，请确认图片数量充足") : DownloadException(message, FFMPEG_FAILED)
    class MediaStoreError(message: String = "保存到相册失败，请检查存储权限") : DownloadException(message, MEDIA_STORE_ERROR)
}