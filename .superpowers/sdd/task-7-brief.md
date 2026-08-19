### Task 7: Batch multi-platform + per-item cloud parse

**Files:**
- Modify: `app/src/main/java/com/douyin/downloader/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/douyin/downloader/ui/home/BatchScreen.kt`

**Interfaces:**
- Consumes: same `parseUrlUseCase` / cloud use case
- Produces:
  - `BatchItem.selectedQualityIndex: Int = 0`
  - `UiState.batchCloudLoadingIds: Set<String>`
  - `onBatchQualitySelected(itemId, index)`
  - `submitBatchItem` uses **item** quality index, not global; resolves cloud if needed before download

- [ ] **Step 1: Extend BatchItem**

```kotlin
data class BatchItem(
    val id: String,
    val rawUrl: String,
    val status: Status,
    val contentInfo: ContentInfo? = null,
    val error: String? = null,
    val selectedQualityIndex: Int = 0,
)
```

Add `batchCloudLoadingIds: Set<String> = emptySet()` to `UiState`.

- [ ] **Step 2: Update batch empty-error copy**

Change「未识别到链接，请确认已粘贴抖音视频链接」→「未识别到链接，请粘贴抖音/视频号/好看/微视/小红书链接」.

Update BatchScreen helper text similarly (multi-platform examples).

- [ ] **Step 3: `onBatchQualitySelected`**

Mirror single-item cloud logic, but update the matching `BatchItem` in `batchItems` and track loading via `batchCloudLoadingIds`. Cache filled cloud url inside that item's `contentInfo.qualities`.

- [ ] **Step 4: Fix `submitBatchItem` quality pick**

```kotlin
val url = run {
    val qualities = info.qualities
    val idx = item.selectedQualityIndex.coerceIn(0, (qualities.size - 1).coerceAtLeast(0))
    val q = qualities.getOrNull(idx)
    when {
        q == null -> info.videoUrl
        q.isCloudParse && q.url.isEmpty() -> {
            // caller must pre-resolve; if still empty, throw
            throw IllegalStateException("云解析未完成")
        }
        q.url.isNotEmpty() -> q.url
        else -> info.videoUrl
    }
}
```

In `onBatchDownloadSelected`, before `submitBatchItem`, for each selected Douyin video whose selected quality is cloud+empty:

```kotlin
viewModelScope.launch {
    for (item in toDownload) {
        val info = item.contentInfo as? ContentInfo.Video ?: run {
            submitBatchItem(item); continue
        }
        val q = info.qualities.getOrNull(item.selectedQualityIndex)
        if (q?.isCloudParse == true && q.url.isEmpty()) {
            try {
                val cloudUrl = resolveDouyinCloudParseUseCase(item.rawUrl)
                val updated = item.copy(
                    contentInfo = info.copy(
                        qualities = info.qualities.map { if (it.isCloudParse) it.copy(url = cloudUrl) else it }
                    ),
                )
                // replace in state then submit
                submitBatchItem(updated)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "「${info.title}」云解析失败，已跳过：${e.message}") }
            }
        } else {
            submitBatchItem(item)
        }
    }
}
```

- [ ] **Step 5: BatchScreen quality chips**

For OK video items with non-empty `qualities`, show `FilterChip` row under the title; wire to `onBatchQualitySelected`. Show tiny progress if `item.id in state.batchCloudLoadingIds`.

- [ ] **Step 6: Manual check**

Batch paste one Douyin + one non-Douyin (or mock failure) → parse → select cloud on Douyin item → download selected.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/douyin/downloader/ui/home/HomeViewModel.kt app/src/main/java/com/douyin/downloader/ui/home/BatchScreen.kt
git commit -m "feat: batch multi-platform parse with per-item cloud quality"
```

---

