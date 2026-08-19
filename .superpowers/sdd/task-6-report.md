# Task 6 Report: HomeViewModel — single-item cloud lazy load

## Status

DONE

## What Was Implemented

### `ResolveDouyinCloudParseUseCase.kt` (new)

- Thin wrapper delegating to `ContentRepository.resolveDouyinCloudParse(rawUrl)`.
- Injected into `HomeViewModel` via Hilt.

### `HomeViewModel.kt`

- Added `UiState.cloudParseLoading: Boolean`.
- Injected `ResolveDouyinCloudParseUseCase`.
- Rewrote `onQualitySelected`:
  - Local qualities: immediate selection.
  - Cloud chip with cached URL: immediate selection (no re-request).
  - Cloud chip with empty URL: sets loading, calls use case, immutably updates `qualities[index].url` via `replaceCloudUrl`.
  - On failure: reverts `selectedQualityIndex` to previous + Chinese error snackbar message.
- Updated `getSelectedVideoUrl()` to fall back to first non-cloud quality / `videoUrl` when selected URL is empty.

### `HomeScreen.kt`

- `VideoDownloadSection`: while `cloudParseLoading` and cloud chip selected, shows `CircularProgressIndicator` in chip label; disables other chips during load.

## Compile Results

**Command:**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin
```

**Result:** `BUILD SUCCESSFUL` (16 tasks, 2 executed).

## Files Changed

| File | Action |
|------|--------|
| `app/src/main/java/com/douyin/downloader/domain/usecase/ResolveDouyinCloudParseUseCase.kt` | Added |
| `app/src/main/java/com/douyin/downloader/ui/home/HomeViewModel.kt` | Modified |
| `app/src/main/java/com/douyin/downloader/ui/home/HomeScreen.kt` | Modified |

## Commit

```
feat: lazy Douyin cloud parse via quality chip
```

## Self-Review

| Check | Result |
|-------|--------|
| `ResolveDouyinCloudParseUseCase` delegates to repository | ✓ |
| 52API only called when user taps empty 云解析 chip | ✓ |
| Cached cloud URL skips re-request on second select | ✓ |
| Failure reverts selection + Chinese error message | ✓ |
| `cloudParseLoading` + chip spinner in UI | ✓ |
| `getSelectedVideoUrl` fallback for empty cloud URL | ✓ |
| Hilt auto-wires new use case | ✓ |
| Compile passes | ✓ |

## Concerns / Follow-ups

1. **Animated section** — `onQualitySelected` handles `ContentInfo.Animated` qualities, but `AnimatedDownloadSection` has no quality chips yet; cloud lazy load UI only in `VideoDownloadSection`.
2. **Batch download** — `submitBatchItem` / `pickVideoUrl` still use local quality index without cloud resolve; likely Task 7+.
3. **Manual network check** — not run on device in this session; verify no `52api.cn/api/douyin` until 云解析 tap.
4. **JAVA_HOME** — compile required explicit Android Studio JBR on this machine.
