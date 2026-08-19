### Task 6: HomeViewModel — single-item cloud lazy load

**Files:**
- Modify: `app/src/main/java/com/douyin/downloader/domain/usecase/ParseUrlUseCase.kt` (optional thin wrapper) **or** inject `ContentRepository` into ViewModel for cloud resolve
- Modify: `app/src/main/java/com/douyin/downloader/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/douyin/downloader/ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: `ContentRepository.resolveDouyinCloudParse`
- Produces:
  - `UiState.cloudParseLoading: Boolean`
  - `fun onQualitySelected(index: Int)` triggers cloud resolve when target `isCloudParse && url.isEmpty()`
  - After success, update `contentInfo.qualities[index].url` immutably

- [ ] **Step 1: Prefer adding `ResolveDouyinCloudParseUseCase`**

```kotlin
class ResolveDouyinCloudParseUseCase @Inject constructor(
    private val repository: ContentRepository,
) {
    suspend operator fun invoke(rawUrl: String): String = repository.resolveDouyinCloudParse(rawUrl)
}
```

Inject into `HomeViewModel`.

- [ ] **Step 2: Extend UiState + selection logic**

```kotlin
val cloudParseLoading: Boolean = false,
```

Keep `lastLocalQualityIndex` (or previous index) when switching to cloud chip.

Rewrite `onQualitySelected`:

```kotlin
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

private fun replaceCloudUrl(info: ContentInfo?, url: String): ContentInfo? = when (info) {
    is ContentInfo.Video -> info.copy(
        qualities = info.qualities.map { if (it.isCloudParse) it.copy(url = url) else it },
        // keep videoUrl as local default; download uses selected quality
    )
    is ContentInfo.Animated -> info.copy(
        qualities = info.qualities.map { if (it.isCloudParse) it.copy(url = url) else it },
    )
    else -> info
}
```

Ensure `getSelectedVideoUrl()` returns cloud url when selected and filled; if somehow empty, fall back to first non-cloud quality.

- [ ] **Step 3: HomeScreen — disable chip / show loading while `cloudParseLoading`**

In `VideoDownloadSection`, when rendering cloud chip (`q.isCloudParse`), if `state.cloudParseLoading && idx == state.selectedQualityIndex`, show small `CircularProgressIndicator` in label or disable other chips briefly.

- [ ] **Step 4: Manual check**

Paste Douyin link → parse → confirm local qualities +「云解析」→ confirm network has **no** call to `52api.cn/api/douyin` yet → tap「云解析」→ wait → download works.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/douyin/downloader/domain/usecase/ResolveDouyinCloudParseUseCase.kt app/src/main/java/com/douyin/downloader/ui/home/HomeViewModel.kt app/src/main/java/com/douyin/downloader/ui/home/HomeScreen.kt
git commit -m "feat: lazy Douyin cloud parse via quality chip"
```

---

