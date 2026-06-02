package com.douyin.downloader.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.douyin.downloader.data.remote.DouyinApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: DouyinApi,
) {
    companion object {
        /**
         * 公开下载目录的相对路径。需要宿主在调用本仓库前确保已获得
         * MANAGE_EXTERNAL_STORAGE 权限（Android 11+）或 WRITE_EXTERNAL_STORAGE
         * 权限（Android 10 及以下）。
         *
         * Downloads/DYSave/ 目录无需手动创建 —— 通过 MediaStore.insert 时
         * 设置 RELATIVE_PATH，Android 10+ 会自动创建；Android 9 及以下我们用
         * Environment.getExternalStoragePublicDirectory() 显式创建。
         */
        val SUBDIR: String = Environment.DIRECTORY_DOWNLOADS + "/DYSave"
    }

    // ---------------------------------------------------------------------
    // 公共保存入口
    // ---------------------------------------------------------------------

    suspend fun saveVideo(bytes: ByteArray, filename: String): Uri = withContext(Dispatchers.IO) {
        val uri = createPendingItem(filename, "video/mp4")
        writeAll(uri, bytes)
        finalize(uri)
        uri
    }

    suspend fun saveVideoStream(
        videoUrl: String,
        filename: String,
        onProgress: (Long, Long?) -> Unit = { _, _ -> },
    ): Uri = withContext(Dispatchers.IO) {
        val uri = createPendingItem(filename, "video/mp4")
        context.contentResolver.openOutputStream(uri)?.use { output ->
            api.streamTo(videoUrl, output, onProgress)
        } ?: throw IllegalStateException("无法打开输出流：$uri")
        finalize(uri)
        uri
    }

    suspend fun saveImage(bytes: ByteArray, filename: String): Uri = withContext(Dispatchers.IO) {
        val uri = createPendingItem(filename, "image/*")
        writeAll(uri, bytes)
        finalize(uri)
        uri
    }

    suspend fun saveImagesZip(imageUrls: List<String>, filename: String): Uri =
        withContext(Dispatchers.IO) {
            val uri = createPendingItem(filename, "application/zip")
            context.contentResolver.openOutputStream(uri)?.use { out ->
                ZipOutputStream(out).use { zos ->
                    imageUrls.forEachIndexed { i, url ->
                        val bytes = api.downloadBytes(url)
                        val ext = inferImageExt(url)
                        zos.putNextEntry(ZipEntry("image_${i + 1}$ext"))
                        zos.write(bytes)
                        zos.closeEntry()
                    }
                }
            } ?: throw IllegalStateException("无法打开输出流：$uri")
            finalize(uri)
            uri
        }

    suspend fun synthesizeVideo(
        imageUrls: List<String>,
        musicUrl: String?,
        duration: Int,
        filename: String,
    ): Uri = withContext(Dispatchers.IO) {
        val tmpDir = File(context.cacheDir, "dy_${System.currentTimeMillis()}")
        tmpDir.mkdirs()

        try {
            val imgPaths = imageUrls.mapIndexed { i, url ->
                val bytes = api.downloadBytes(url)
                val ext = inferImageExt(url)
                val file = File(tmpDir, "img_$i$ext")
                file.writeBytes(bytes)
                file.absolutePath
            }

            val musicPath = musicUrl?.let {
                val bytes = api.downloadBytes(it)
                val file = File(tmpDir, "music.mp3")
                file.writeBytes(bytes)
                file.absolutePath
            }

            val outputFile = File(tmpDir, "output.mp4")
            val actualDuration = if (duration > 0) duration else 10
            val command = if (imgPaths.size == 1) {
                buildSingleImageCommand(imgPaths[0], musicPath, actualDuration, outputFile.absolutePath)
            } else {
                buildSlideshowCommand(tmpDir, imgPaths, musicPath, actualDuration, outputFile.absolutePath)
            }

            val session = FFmpegKit.execute(command)
            if (session.returnCode.isValueError) {
                throw IllegalStateException("ffmpeg 失败: ${session.output.takeLast(500)}")
            }

            val bytes = outputFile.readBytes()
            val uri = createPendingItem(filename, "video/mp4")
            writeAll(uri, bytes)
            finalize(uri)
            uri
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    suspend fun mergeVideoMusic(
        videoUrl: String,
        musicUrl: String?,
        filename: String,
    ): Uri = withContext(Dispatchers.IO) {
        if (musicUrl.isNullOrEmpty()) {
            val bytes = api.downloadBytes(videoUrl, timeoutSeconds = 60)
            val uri = createPendingItem(filename, "video/mp4")
            writeAll(uri, bytes)
            finalize(uri)
            return@withContext uri
        }

        val tmpDir = File(context.cacheDir, "dy_merge_${System.currentTimeMillis()}")
        tmpDir.mkdirs()

        try {
            val videoFile = File(tmpDir, "video.mp4")
            videoFile.writeBytes(api.downloadBytes(videoUrl, timeoutSeconds = 60))

            val musicFile = File(tmpDir, "music.mp3")
            musicFile.writeBytes(api.downloadBytes(musicUrl))

            val outputFile = File(tmpDir, "output.mp4")
            val command = "-i ${videoFile.absolutePath} -i ${musicFile.absolutePath} " +
                "-c:v copy -c:a aac -b:a 192k -map 0:v:0 -map 1:a:0 -shortest " +
                outputFile.absolutePath

            val session = FFmpegKit.execute(command)
            if (session.returnCode.isValueError) {
                throw IllegalStateException("ffmpeg 合并失败: ${session.output.takeLast(500)}")
            }

            val bytes = outputFile.readBytes()
            val uri = createPendingItem(filename, "video/mp4")
            writeAll(uri, bytes)
            finalize(uri)
            uri
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------------
    // MediaStore 私有工具
    // ---------------------------------------------------------------------

    private fun createPendingItem(filename: String, mimeType: String): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 9 及以下：直接走文件系统，调用方需 WRITE_EXTERNAL_STORAGE
            ensureLegacyDir()
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, SUBDIR)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        return context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values,
        ) ?: throw IllegalStateException(
            "无法创建 MediaStore 条目：可能缺少存储权限或文件名冲突（$filename）"
        )
    }

    private fun writeAll(uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IllegalStateException("无法写入：$uri")
    }

    private fun finalize(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            context.contentResolver.update(uri, values, null, null)
        }
    }

    @Suppress("DEPRECATION")
    private fun ensureLegacyDir() {
        val legacy = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "DYSave",
        )
        if (!legacy.exists()) legacy.mkdirs()
    }

    // ---------------------------------------------------------------------
    // FFmpeg 命令构造
    // ---------------------------------------------------------------------

    private fun buildSingleImageCommand(
        imgPath: String,
        musicPath: String?,
        duration: Int,
        outputPath: String,
    ): String {
        val sb = StringBuilder()
        sb.append("-y -loop 1 -i $imgPath ")
        if (musicPath != null) sb.append("-i $musicPath ")
        sb.append("-c:v libopenh264 -c:a aac -b:a 192k -pix_fmt yuv420p ")
        sb.append("-t $duration -shortest $outputPath")
        return sb.toString()
    }

    private fun buildSlideshowCommand(
        tmpDir: File,
        imgPaths: List<String>,
        musicPath: String?,
        duration: Int,
        outputPath: String,
    ): String {
        val perImage = maxOf(duration.toDouble() / imgPaths.size, 2.0)
        val concatFile = File(tmpDir, "concat.txt")
        concatFile.bufferedWriter().use { writer ->
            imgPaths.forEach { path ->
                writer.write("file '$path'\n")
                writer.write("duration $perImage\n")
            }
            writer.write("file '${imgPaths.last()}'\n")
        }

        val sb = StringBuilder()
        sb.append("-y -f concat -safe 0 -i ${concatFile.absolutePath} ")
        if (musicPath != null) sb.append("-i $musicPath ")
        sb.append("-c:v libopenh264 -pix_fmt yuv420p -c:a aac -b:a 192k ")
        sb.append("-t $duration -shortest $outputPath")
        return sb.toString()
    }

    private fun inferImageExt(url: String): String {
        val lower = url.lowercase()
        return when {
            ".jpeg" in lower || ".jpg" in lower -> ".jpg"
            ".png" in lower -> ".png"
            else -> ".webp"
        }
    }
}
