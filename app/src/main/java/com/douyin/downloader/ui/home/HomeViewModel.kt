package com.douyin.downloader.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.douyin.downloader.data.local.HistoryDao
import com.douyin.downloader.data.local.HistoryEntity
import com.douyin.downloader.data.local.SettingsRepository
import com.douyin.downloader.data.local.StoragePermissionState
import com.douyin.downloader.data.model.ContentInfo
import com.douyin.downloader.data.repository.DownloadManager
import com.douyin.downloader.domain.usecase.DownloadImagesUseCase
import com.douyin.downloader.domain.usecase.DownloadVideoUseCase
import com.douyin.downloader.domain.usecase.ParseUrlUseCase
import com.douyin.downloader.domain.usecase.ResolveDouyinCloudParseUseCase
import com.douyin.downloader.domain.usecase.SynthesizeVideoUseCase
import com.douyin.downloader.util.TitleFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import javax.inject.Inject

/**
 * 主页 ViewModel：负责链接解析 + 当前内容选择（清晰度 / 多选图）。
 * **不再**承担下载执行 / 历史 / 任务编排 —— 这些下放给 [DownloadCenterViewModel]，
 * 下载入口通过 [DownloadManager]（@Singleton 服务层）统一调度。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val parseUrlUseCase: ParseUrlUseCase,
    private val resolveDouyinCloudParseUseCase: ResolveDouyinCloudParseUseCase,
    private val downloadVideoUseCase: DownloadVideoUseCase,
    private val downloadImagesUseCase: DownloadImagesUseCase,
    private val synthesizeVideoUseCase: SynthesizeVideoUseCase,
    private val historyDao: HistoryDao,
    private val storagePermission: StoragePermissionState,
    private val downloadManager: DownloadManager,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** 用户设置：清晰度偏好 / 并发数 / 子目录。 */
    val settings = settingsRepository.flow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsRepository.Settings(),
    )

    /**
     * 批量解析后的单条结果。携带 rawUrl 作 id 方便勾选映射，
     * 失败项只保留错误信息，不影响其他项展示。
     */
    data class BatchItem(
        val id: String,
        val rawUrl: String,
        val status: Status,
        val contentInfo: ContentInfo? = null,
        val error: String? = null,
        val selectedQualityIndex: Int = 0,
    ) {
        enum class Status { OK, FAILED }
    }

    data class UiState(
        val inputUrl: String = "",
        val isLoading: Boolean = false,
        val loadingMessage: String = "",
        val error: String? = null,
        val contentInfo: ContentInfo? = null,
        val galleryIndex: Int = 0,
        val selectedImages: Set<Int> = emptySet(),
        val selectedQualityIndex: Int = 0,
        val cloudParseLoading: Boolean = false,
        val history: List<HistoryEntity> = emptyList(),
        // ---- 批量下载 ----
        val batchInput: String = "",
        val batchItems: List<BatchItem> = emptyList(),
        val batchSelectedIds: Set<String> = emptySet(),
        val batchIsParsing: Boolean = false,
        val batchParseMessage: String = "",
        val batchCloudLoadingIds: Set<String> = emptySet(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * 挂起的下载：用户在无权限状态点击下载时，把意图暂存；授权回来时由
     * [onPermissionResumed] 重放。
     */
    private var pendingDownload: (suspend () -> Unit)? = null

    init {
        viewModelScope.launch {
            historyDao.getRecent().collect { items ->
                _uiState.update { it.copy(history = items) }
            }
        }
    }

    // ---------- 解析与选择 ----------

    fun onUrlChanged(url: String) {
        _uiState.update { it.copy(inputUrl = url, error = null) }
    }

    fun onPaste(text: String) {
        _uiState.update { it.copy(inputUrl = text) }
        onParse()
    }

    fun onParse() {
        val url = _uiState.value.inputUrl.trim()
        if (url.isEmpty()) return

        _uiState.update {
            it.copy(isLoading = true, loadingMessage = "正在解析...", error = null, contentInfo = null)
        }

        viewModelScope.launch {
            try {
                val info = parseUrlUseCase(url)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        contentInfo = info,
                        galleryIndex = 0,
                        selectedImages = getImages(info).indices.toSet(),
                        selectedQualityIndex = 0,
                    )
                }
                addToHistory(info)
            } catch (e: Exception) {
                val msg = e.message ?: "解析失败，请检查链接是否有效"
                val parseEx = if (e is com.douyin.downloader.data.model.ParseException) e else null
                _uiState.update {
                    it.copy(isLoading = false, error = parseEx?.message ?: msg)
                }
            }
        }
    }

    fun onGalleryIndexChanged(index: Int) {
        _uiState.update { it.copy(galleryIndex = index) }
    }

    fun onImageToggled(index: Int) {
        _uiState.update { state ->
            val selected = state.selectedImages.toMutableSet()
            if (index in selected) selected.remove(index) else selected.add(index)
            state.copy(selectedImages = selected)
        }
    }

    fun onSelectAll() {
        _uiState.update { state ->
            val images = getImages(state.contentInfo)
            val allSelected = state.selectedImages.size == images.size
            state.copy(selectedImages = if (allSelected) emptySet() else images.indices.toSet())
        }
    }

    fun onQualitySelected(index: Int) {
        val state = _uiState.value
        val info = state.contentInfo ?: return
        val qualities = when (info) {
            is ContentInfo.Video -> info.qualities
            is ContentInfo.Animated -> info.qualities
            else -> return
        }
        if (index !in qualities.indices) return
        val q = qualities[index]
        if (!q.isCloudParse) {
            _uiState.update { it.copy(selectedQualityIndex = index) }
            return
        }
        if (q.url.isNotEmpty()) {
            _uiState.update { it.copy(selectedQualityIndex = index) }
            return
        }
        val raw = state.inputUrl
        val previous = state.selectedQualityIndex
        _uiState.update { it.copy(selectedQualityIndex = index, cloudParseLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val url = resolveDouyinCloudParseUseCase(raw)
                _uiState.update { s ->
                    val updated = replaceCloudUrl(s.contentInfo, url)
                    s.copy(contentInfo = updated, cloudParseLoading = false, selectedQualityIndex = index)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        cloudParseLoading = false,
                        selectedQualityIndex = previous,
                        error = "云解析失败：${e.message ?: "未知错误"}，可改用本地清晰度",
                    )
                }
            }
        }
    }

    fun getSelectedVideoUrl(): String {
        val info = _uiState.value.contentInfo ?: return ""
        val qualities = when (info) {
            is ContentInfo.Video -> info.qualities
            is ContentInfo.Animated -> info.qualities
            else -> emptyList()
        }
        if (qualities.isEmpty()) {
            return when (info) {
                is ContentInfo.Video -> info.videoUrl
                is ContentInfo.Animated -> info.videoUrl
                else -> ""
            }
        }
        val userIdx = _uiState.value.selectedQualityIndex
        val preference = settings.value.defaultQuality
        val idx = when (preference) {
            SettingsRepository.DefaultQuality.Auto ->
                if (userIdx in qualities.indices) userIdx else 0
            SettingsRepository.DefaultQuality.Highest ->
                qualities.indexOfFirst { !it.isCloudParse }.takeIf { it >= 0 } ?: 0
            SettingsRepository.DefaultQuality.Lowest -> 0
        }.coerceIn(0, qualities.size - 1)
        val url = qualities[idx].url
        if (url.isNotEmpty()) return url
        return qualities.firstOrNull { !it.isCloudParse && it.url.isNotEmpty() }?.url
            ?: when (info) {
                is ContentInfo.Video -> info.videoUrl
                is ContentInfo.Animated -> info.videoUrl
                else -> ""
            }
    }

    private fun replaceCloudUrl(info: ContentInfo?, url: String): ContentInfo? = when (info) {
        is ContentInfo.Video -> info.copy(
            qualities = info.qualities.map { if (it.isCloudParse) it.copy(url = url) else it },
        )
        is ContentInfo.Animated -> info.copy(
            qualities = info.qualities.map { if (it.isCloudParse) it.copy(url = url) else it },
        )
        else -> info
    }

    // ---------- 下载入口（投递意图到 DownloadManager）----------

    fun onDownloadVideo() {
        val info = _uiState.value.contentInfo ?: return
        val videoUrl = getSelectedVideoUrl()
        if (videoUrl.isEmpty()) return

        val stem = TitleFormatter.formatFilenameStem(info.author, info.title)
        val filename = "$stem.mp4"
        val displayName = TitleFormatter.formatDisplayName(info.author, info.title)
        val shareUrl = _uiState.value.inputUrl.takeIf { it.isNotEmpty() }
        submit(displayName, "video/mp4") { _, onProgress, _ ->
            downloadVideoUseCase(videoUrl, filename, onProgress = { downloaded, total ->
                if (total != null) onProgress(downloaded, total)
            }, shareUrl = shareUrl)
        }
    }

    fun onDownloadCurrentImage() {
        val state = _uiState.value
        val images = getImages(state.contentInfo)
        if (images.isEmpty()) return
        val url = images[state.galleryIndex]
        val info = state.contentInfo
        val stem = TitleFormatter.formatFilenameStem(info?.author.orEmpty(), info?.title.orEmpty())
        val filename = "${stem}_图${state.galleryIndex + 1}.jpg"
        val displayName = TitleFormatter.formatDisplayName(
            author = info?.author.orEmpty(),
            title = info?.title.orEmpty(),
            suffix = "_图${state.galleryIndex + 1}",
        )
        submit(displayName, "image/*") { _, _, _ ->
            downloadImagesUseCase.downloadSingle(url, filename)
        }
    }

    fun onDownloadSelectedImages() {
        val state = _uiState.value
        val images = getImages(state.contentInfo)
        if (images.isEmpty()) return
        val selected = state.selectedImages.sorted()
        if (selected.isEmpty()) return
        val urls = selected.map { images[it] }
        val total = urls.size

        val info = _uiState.value.contentInfo
        val stem = TitleFormatter.formatFilenameStem(info?.author.orEmpty(), info?.title.orEmpty())
        val filenames = selected.map { "${stem}_图${it + 1}.jpg" }

        val displayName = TitleFormatter.formatDisplayName(
            author = info?.author.orEmpty(),
            title = info?.title.orEmpty(),
            suffix = "_${total}张图",
        )
        submit(displayName, "image/*") { _, onProgress, _ ->
            val uris = downloadImagesUseCase.downloadMultiple(urls, filenames) { completed, _ ->
                val fraction = completed.toFloat() / total
                onProgress((fraction * 1000).toLong(), 1000)
            }
            uris.firstOrNull()
        }
        _uiState.update { it.copy(selectedImages = emptySet()) }
    }

    fun onSynthesizeVideo() {
        val info = _uiState.value.contentInfo
        val images = getImages(info)
        if (images.isEmpty()) return

        val musicUrl = when (info) {
            is ContentInfo.ImageGallery -> info.musicUrl
            is ContentInfo.Animated -> info.musicUrl
            else -> null
        }
        val duration = when (info) {
            is ContentInfo.ImageGallery -> info.duration
            is ContentInfo.Animated -> info.duration
            else -> 0
        }

        val stem = TitleFormatter.formatFilenameStem(info?.author.orEmpty(), info?.title.orEmpty())
        val filename = "${stem}_合成.mp4"
        val displayName = TitleFormatter.formatDisplayName(
            author = info?.author.orEmpty(),
            title = info?.title.orEmpty(),
            suffix = "_合成",
        )
        submit(displayName, "video/mp4") { _, _, _ ->
            synthesizeVideoUseCase.fromImages(images, musicUrl, duration, filename)
        }
    }

    fun onMergeAnimatedVideo() {
        val info = _uiState.value.contentInfo as? ContentInfo.Animated ?: return
        val videoUrl = getSelectedVideoUrl().ifEmpty { info.videoUrl }
        if (videoUrl.isEmpty()) return

        val stem = TitleFormatter.formatFilenameStem(info.author, info.title)
        val filename = "${stem}_合并.mp4"
        val displayName = TitleFormatter.formatDisplayName(
            author = info.author,
            title = info.title,
            suffix = "_合并",
        )
        submit(displayName, "video/mp4") { _, _, _ ->
            synthesizeVideoUseCase.mergeWithMusic(videoUrl, info.musicUrl, filename)
        }
    }

    // ---------- 批量解析 + 批量下载 ----------

    fun onBatchInputChanged(text: String) {
        _uiState.update { it.copy(batchInput = text) }
    }

    /**
     * 解析批量输入。策略：
     * 1. 把整段输入按行切，每行再用一个 URL 正则抽 `http(s)://...`；
     *    这样支持直接粘贴「抖音分享长文本」也支持一行一个 URL；
     * 2. 跳过空行 / 注释行（以 # 开头）；
     * 3. 并发解析（最多 3 个并发），单条失败不影响其他；
     * 4. 解析成功时调用现有 addToHistory 入历史；
     * 5. 列表 key 用「aweme_id（成功项）/ 原文+序号（失败项）」保证唯一。
     */
    fun onBatchParse() {
        val raw = _uiState.value.batchInput
        val urls = extractUrls(raw)
        if (urls.isEmpty()) {
            _uiState.update {
                it.copy(error = "未识别到链接，请粘贴抖音/视频号/好看/微视/小红书链接")
            }
            return
        }

        _uiState.update {
            it.copy(
                batchIsParsing = true,
                batchParseMessage = "正在解析 0/${urls.size}...",
                batchItems = emptyList(),
                batchSelectedIds = emptySet(),
                batchCloudLoadingIds = emptySet(),
                error = null,
            )
        }

        viewModelScope.launch {
            val results = coroutineScope {
                // 并发从 3 降到 2，配合 DouyinApi 令牌桶（700ms/req），
                // 实际峰值 ~3 req/s（1.4 双并发 + 1 个 download 反向心跳），
                // 避免触发抖音 WAF 频率封禁。
                val semaphore = Semaphore(permits = 2)
                val done = java.util.concurrent.atomic.AtomicInteger(0)
                urls.mapIndexed { index, url ->
                    async {
                        semaphore.withPermit {
                            val item = try {
                                val info = parseUrlUseCase(url)
                                addToHistory(info)
                                // 成功项：id 用 aweme_id，重复粘贴同一视频不会冲突
                                BatchItem(
                                    id = "${info.id}#$index",
                                    rawUrl = url,
                                    status = BatchItem.Status.OK,
                                    contentInfo = info,
                                )
                            } catch (e: Exception) {
                                // 失败项：用 index 保证 key 唯一（即使两条 rawUrl 一样）
                                BatchItem(
                                    id = "failed#$index",
                                    rawUrl = url,
                                    status = BatchItem.Status.FAILED,
                                    error = e.message ?: "解析失败",
                                )
                            }
                            val n = done.incrementAndGet()
                            _uiState.update {
                                it.copy(batchParseMessage = "正在解析 $n/${urls.size}...")
                            }
                            item
                        }
                    }
                }.awaitAll()
            }
            val ok = results.count { it.status == BatchItem.Status.OK }
            val fail = results.size - ok
            // 去重：相同 aweme_id 只保留第一条
            val deduped = mutableListOf<BatchItem>()
            val seen = mutableSetOf<String>()
            for (item in results) {
                val key = (item.contentInfo?.id) ?: item.rawUrl
                if (seen.add(key)) deduped.add(item)
            }
            _uiState.update {
                it.copy(
                    batchIsParsing = false,
                    batchParseMessage = if (fail == 0) "全部解析完成（$ok/${results.size}）"
                    else "完成：$ok 成功，$fail 失败",
                    batchItems = deduped,
                    batchSelectedIds = deduped
                        .filter { it.status == BatchItem.Status.OK }
                        .map { it.id }
                        .toSet(),
                )
            }
        }
    }

    companion object {
        private val URL_REGEX = Regex("""https?://[A-Za-z0-9\-._~:/?#@!$&'()*+,;=%]+""")

        /**
         * 从混合文本中抽取 URL：
         * 1. 先按行切；空行 / # 注释行跳过；
         * 2. 每行再用正则抽 URL（支持"一行多 URL"和"分享长文本里夹 URL"）。
         * 3. 同一 URL 多次出现只保留一份。
         */
        internal fun extractUrls(input: String): List<String> {
            val seen = LinkedHashSet<String>()
            for (rawLine in input.lines()) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                for (m in URL_REGEX.findAll(line)) {
                    var u = m.value
                    // 去掉尾部中文/英文标点（分享文案经常"… 09/12 teB:/"跟在 URL 后面）
                    u = u.trimEnd(
                        '，', '。', ',', '.', ';', '；', ':', '：',
                        '!', '！', '?', '？', '、', ')', '）', ']', '】',
                    )
                    seen.add(u)
                }
            }
            return seen.toList()
        }
    }

    fun onBatchItemToggle(id: String) {
        _uiState.update { state ->
            val next = state.batchSelectedIds.toMutableSet()
            if (id in next) next.remove(id) else next.add(id)
            state.copy(batchSelectedIds = next)
        }
    }

    fun onBatchSelectAll() {
        val items = _uiState.value.batchItems
        val okIds = items.filter { it.status == BatchItem.Status.OK }.map { it.id }.toSet()
        _uiState.update { state ->
            val shouldSelectAll = state.batchSelectedIds.size < okIds.size
            state.copy(batchSelectedIds = if (shouldSelectAll) okIds else emptySet())
        }
    }

    fun onBatchQualitySelected(itemId: String, index: Int) {
        val item = _uiState.value.batchItems.find { it.id == itemId } ?: return
        val info = item.contentInfo ?: return
        val qualities = when (info) {
            is ContentInfo.Video -> info.qualities
            is ContentInfo.Animated -> info.qualities
            else -> return
        }
        if (index !in qualities.indices) return
        val q = qualities[index]
        if (!q.isCloudParse || q.url.isNotEmpty()) {
            updateBatchItem(itemId) { it.copy(selectedQualityIndex = index) }
            return
        }
        val previous = item.selectedQualityIndex
        _uiState.update {
            it.copy(
                batchCloudLoadingIds = it.batchCloudLoadingIds + itemId,
                error = null,
            )
        }
        updateBatchItem(itemId) { it.copy(selectedQualityIndex = index) }
        viewModelScope.launch {
            try {
                val url = resolveDouyinCloudParseUseCase(item.rawUrl)
                _uiState.update { s ->
                    s.copy(
                        batchItems = s.batchItems.map { bi ->
                            if (bi.id != itemId) bi
                            else bi.copy(
                                contentInfo = replaceCloudUrl(bi.contentInfo, url),
                                selectedQualityIndex = index,
                            )
                        },
                        batchCloudLoadingIds = s.batchCloudLoadingIds - itemId,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { s ->
                    s.copy(
                        batchItems = s.batchItems.map { bi ->
                            if (bi.id != itemId) bi else bi.copy(selectedQualityIndex = previous)
                        },
                        batchCloudLoadingIds = s.batchCloudLoadingIds - itemId,
                        error = "云解析失败：${e.message ?: "未知错误"}，可改用本地清晰度",
                    )
                }
            }
        }
    }

    fun onBatchDownloadSelected() {
        val items = _uiState.value.batchItems
        val selectedIds = _uiState.value.batchSelectedIds
        if (selectedIds.isEmpty()) {
            _uiState.update { it.copy(error = "未选择任何项目") }
            return
        }
        val toDownload = items.filter { it.id in selectedIds && it.status == BatchItem.Status.OK }
        val downloadAction: suspend () -> Unit = {
            for (item in toDownload) {
                val info = item.contentInfo as? ContentInfo.Video
                if (info == null) {
                    submitBatchItem(item)
                    continue
                }
                val q = info.qualities.getOrNull(item.selectedQualityIndex)
                if (q?.isCloudParse == true && q.url.isEmpty()) {
                    try {
                        val cloudUrl = resolveDouyinCloudParseUseCase(item.rawUrl)
                        val updated = item.copy(
                            contentInfo = info.copy(
                                qualities = info.qualities.map {
                                    if (it.isCloudParse) it.copy(url = cloudUrl) else it
                                },
                            ),
                        )
                        _uiState.update { s ->
                            s.copy(batchItems = s.batchItems.map { if (it.id == item.id) updated else it })
                        }
                        submitBatchItem(updated)
                    } catch (e: Exception) {
                        _uiState.update {
                            it.copy(error = "「${info.title}」云解析失败，已跳过：${e.message}")
                        }
                    }
                } else {
                    submitBatchItem(item)
                }
            }
        }
        if (!storagePermission.hasPermission()) {
            pendingBatchDownload = downloadAction
            storagePermission.request(appContext)
            return
        }
        viewModelScope.launch { downloadAction() }
    }

    private var pendingBatchDownload: (suspend () -> Unit)? = null

    private fun updateBatchItem(itemId: String, transform: (BatchItem) -> BatchItem) {
        _uiState.update { s ->
            s.copy(batchItems = s.batchItems.map { if (it.id == itemId) transform(it) else it })
        }
    }

    private fun submitBatchItem(item: BatchItem) {
        val info = item.contentInfo ?: return
        when (info) {
            is ContentInfo.Video -> {
                val stem = TitleFormatter.formatFilenameStem(info.author, info.title)
                val filename = "$stem.mp4"
                val displayName = TitleFormatter.formatDisplayName(info.author, info.title)
                val shareUrl = item.rawUrl.takeIf { it.isNotEmpty() }
                val videoInfo = info
                val qualityIndex = item.selectedQualityIndex
                submit(displayName, "video/mp4") { _, onProgress, _ ->
                    val url = pickBatchVideoUrl(videoInfo, qualityIndex)
                    downloadVideoUseCase(url, filename, onProgress = { downloaded, total ->
                        if (total != null) onProgress(downloaded, total)
                    }, shareUrl = shareUrl)
                }
            }
            is ContentInfo.ImageGallery -> {
                val stem = TitleFormatter.formatFilenameStem(info.author, info.title)
                val displayName = TitleFormatter.formatDisplayName(info.author, info.title, suffix = "_${info.images.size}张图")
                submit(displayName, "image/*") { _, onProgress, _ ->
                    val filenames = info.images.mapIndexed { i, _ -> "${stem}_图${i + 1}.jpg" }
                    val uris = downloadImagesUseCase.downloadMultiple(info.images, filenames) { c, _ ->
                        onProgress(c.toLong(), info.images.size.toLong())
                    }
                    uris.firstOrNull()
                }
            }
            is ContentInfo.Animated -> {
                val stem = TitleFormatter.formatFilenameStem(info.author, info.title)
                val filename = "${stem}_合成.mp4"
                val displayName = TitleFormatter.formatDisplayName(info.author, info.title, suffix = "_合成")
                submit(displayName, "video/mp4") { _, _, _ ->
                    synthesizeVideoUseCase.fromImages(info.images, info.musicUrl, info.duration, filename)
                }
            }
        }
    }

    private fun pickBatchVideoUrl(info: ContentInfo.Video, selectedIdx: Int): String {
        val qualities = info.qualities
        val idx = selectedIdx.coerceIn(0, (qualities.size - 1).coerceAtLeast(0))
        val q = qualities.getOrNull(idx)
        return when {
            q == null -> info.videoUrl
            q.isCloudParse && q.url.isEmpty() -> throw IllegalStateException("云解析未完成")
            q.url.isNotEmpty() -> q.url
            else -> info.videoUrl
        }
    }

    /**
     * 选画质：复用 settings.defaultQuality 偏好。
     */
    private fun pickVideoUrl(info: ContentInfo, defaultIdx: (Int) -> Int): String {
        val qualities = when (info) {
            is ContentInfo.Video -> info.qualities
            is ContentInfo.Animated -> info.qualities
            else -> return ""
        }
        if (qualities.isEmpty()) {
            return when (info) {
                is ContentInfo.Video -> info.videoUrl
                is ContentInfo.Animated -> info.videoUrl
                else -> ""
            }
        }
        val userIdx = _uiState.value.selectedQualityIndex
        val preference = settings.value.defaultQuality
        val idx = when (preference) {
            SettingsRepository.DefaultQuality.Auto ->
                if (userIdx in qualities.indices) userIdx else 0
            SettingsRepository.DefaultQuality.Highest ->
                qualities.indexOfFirst { !it.isCloudParse }.takeIf { it >= 0 } ?: 0
            SettingsRepository.DefaultQuality.Lowest -> 0
        }.coerceIn(0, qualities.size - 1)
        return qualities[idx].url
    }

    // ---------- 权限 + 调度 ----------

    private fun submit(
        name: String,
        mimeType: String?,
        block: suspend (taskId: Long, onProgress: (Long, Long?) -> Unit, isPaused: () -> Boolean) -> Uri?,
    ) {
        if (!storagePermission.hasPermission()) {
            pendingDownload = { downloadManager.enqueue(name, mimeType, block) }
            storagePermission.request(appContext)
            return
        }
        downloadManager.enqueue(name, mimeType, block)
    }

    fun onPermissionResumed() {
        storagePermission.refresh()
        val single = pendingDownload
        val batch = pendingBatchDownload
        if (!storagePermission.hasPermission()) return
        if (single != null) {
            pendingDownload = null
            viewModelScope.launch { single.invoke() }
        }
        if (batch != null) {
            pendingBatchDownload = null
            viewModelScope.launch { batch.invoke() }
        }
    }

    // ---------- 历史 ----------

    fun onHistoryItemClicked(entity: HistoryEntity) {
        val info = when (entity.type) {
            "video" -> ContentInfo.Video(
                id = entity.id,
                title = entity.title,
                author = entity.author,
                cover = entity.cover,
                videoUrl = entity.videoUrl,
            )
            "image" -> ContentInfo.ImageGallery(
                id = entity.id,
                title = entity.title,
                author = entity.author,
                cover = entity.cover,
                images = parseJsonArray(entity.images),
                musicUrl = entity.musicUrl,
                duration = entity.duration,
            )
            "animated" -> ContentInfo.Animated(
                id = entity.id,
                title = entity.title,
                author = entity.author,
                cover = entity.cover,
                images = parseJsonArray(entity.images),
                musicUrl = entity.musicUrl,
                duration = entity.duration,
                videoUrl = entity.videoUrl,
            )
            else -> return
        }
        _uiState.update {
            it.copy(
                contentInfo = info,
                galleryIndex = 0,
                selectedImages = getImages(info).indices.toSet(),
                error = null,
            )
        }
    }

    fun onClearHistory() {
        viewModelScope.launch {
            historyDao.clearAll()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ---------- 内部工具 ----------

    private fun addToHistory(info: ContentInfo) {
        viewModelScope.launch {
            val entity = HistoryEntity(
                id = info.id,
                type = when (info) {
                    is ContentInfo.Video -> "video"
                    is ContentInfo.ImageGallery -> "image"
                    is ContentInfo.Animated -> "animated"
                },
                title = info.title,
                author = info.author,
                cover = info.cover,
                videoUrl = when (info) {
                    is ContentInfo.Video -> info.videoUrl
                    is ContentInfo.Animated -> info.videoUrl
                    else -> ""
                },
                images = toJsonArray(getImages(info)),
                musicUrl = when (info) {
                    is ContentInfo.ImageGallery -> info.musicUrl
                    is ContentInfo.Animated -> info.musicUrl
                    else -> ""
                },
                duration = when (info) {
                    is ContentInfo.ImageGallery -> info.duration
                    is ContentInfo.Animated -> info.duration
                    else -> 0
                },
            )
            historyDao.insert(entity)
        }
    }

    private fun getImages(info: ContentInfo?): List<String> = when (info) {
        is ContentInfo.ImageGallery -> info.images
        is ContentInfo.Animated -> info.images
        else -> emptyList()
    }

    private fun toJsonArray(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun parseJsonArray(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
