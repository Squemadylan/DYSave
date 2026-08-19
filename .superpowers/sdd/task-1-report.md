# Task 1 Report: Platform detector + unit tests

## Status

DONE

## What Was Implemented

### `LinkPlatform` enum (`app/src/main/java/com/douyin/downloader/data/model/LinkPlatform.kt`)

Six values: `DOUYIN`, `SHIPINHAO`, `HAOKAN`, `WEISHI`, `XIAOHONGSHU`, `UNKNOWN`.

### `LinkPlatformDetector` object (`app/src/main/java/com/douyin/downloader/data/remote/LinkPlatformDetector.kt`)

- `extractUrl(raw: String): String?` — finds the first `http(s)` URL in pasted share text via regex, trims trailing Chinese/Latin punctuation.
- `detect(rawOrUrl: String): LinkPlatform` — extracts URL (or uses input as-is), lowercases, matches host/path patterns per platform.

### Unit tests (`app/src/test/java/com/douyin/downloader/data/remote/LinkPlatformDetectorTest.kt`)

Seven tests covering all platforms, unknown URLs, and share-text extraction.

### Build config (`app/build.gradle.kts`)

Added `testImplementation("junit:junit:4.13.2")`.

## TDD Evidence

### RED — tests before implementation

**Command:**

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:testDebugUnitTest --tests com.douyin.downloader.data.remote.LinkPlatformDetectorTest
```

**Result:** `BUILD FAILED` — `compileDebugUnitTestKotlin FAILED`

**Relevant output:**

```
e: .../LinkPlatformDetectorTest.kt:3:41 Unresolved reference 'LinkPlatform'.
e: .../LinkPlatformDetectorTest.kt:9:43 Unresolved reference 'LinkPlatformDetector'.
...
FAILURE: Build failed with an exception.
Execution failed for task ':app:compileDebugUnitTestKotlin'.
BUILD FAILED in 12m 59s
```

Classes did not exist yet; compile failure as expected.

### GREEN — after implementation

**Command:** same as above.

**Result:** `BUILD SUCCESSFUL in 16s`

```
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 16s
31 actionable tasks: 11 executed, 20 up-to-date
```

All 7 tests passed.

## Files Changed

| File | Action |
|------|--------|
| `app/build.gradle.kts` | Modified — JUnit 4.13.2 test dependency |
| `app/src/main/java/com/douyin/downloader/data/model/LinkPlatform.kt` | Created |
| `app/src/main/java/com/douyin/downloader/data/remote/LinkPlatformDetector.kt` | Created |
| `app/src/test/java/com/douyin/downloader/data/remote/LinkPlatformDetectorTest.kt` | Created |

## Commit

```
da96084 feat: add link platform detector for multi-source paste
```

## Self-Review

### Correctness

- All brief-specified test cases pass verbatim.
- `extractUrl` handles share text with Chinese punctuation around the URL.
- `detect` uses `extractUrl` first, so share-text input works without separate caller logic.
- Douyin matching covers `v.douyin.com`, `iesdouyin.com`, and generic `douyin.com` paths.
- Shipinhao distinguishes `/sph` paths on `weixin.qq.com` from other WeChat URLs.
- Xiaohongshu includes `xhslink.com`, `xiaohongshu.com`, and `xhs.cn` via regex.

### Code quality

- Matches existing project layout (`data/model`, `data/remote`).
- `object` singleton pattern consistent with other remote utilities.
- `Locale.ROOT` used for case-insensitive host matching (locale-safe).
- No unnecessary abstractions; scope limited to detector only.

### Test coverage

- One test per platform + unknown + share-text extraction.
- No integration or Android instrumentation tests (appropriate for pure logic).

## Concerns

1. **JAVA_HOME** — Shell did not have `JAVA_HOME` set; tests required `JAVA_HOME` pointing to Android Studio JBR. CI/local scripts may need the same.
2. **First Gradle run** — Cold start downloaded Gradle 9.0.0 and took ~13 minutes; subsequent run was ~16s.
3. **URL regex** — Brief-specified regex may not capture every edge-case URL (e.g. unusual encodings); sufficient for known share-link formats.
4. **Douyin broad match** — `douyin.com/` substring could theoretically match unrelated hosts containing that string; unlikely in practice for paste URLs.

## Next Steps (out of scope for Task 1)

- Task 2+ will wire `LinkPlatformDetector` into paste/parse routing.
- Consider adding tests for `channels.weixin.qq.com` and `haokan.baidu.com` variants when parsers are integrated.
