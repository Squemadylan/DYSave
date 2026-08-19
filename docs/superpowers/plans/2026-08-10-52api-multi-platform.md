# 52API Multi-Platform Parse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auto-detect pasted short-video links (抖音/视频号/好看/微视/小红书), parse non-Douyin via 52API, and add Douyin「云解析」as a lazy quality option while keeping local Douyin HTML parse unchanged.

**Architecture:** `LinkPlatformDetector` routes URLs; Douyin stays on existing `ContentRepository` local path and appends a `VideoQuality(isCloudParse=true)` placeholder; other platforms call `FiftyTwoApiClient`. Cloud parse runs only when the user selects「云解析」(single + batch). XHS cookie is stored in DataStore via Settings; API key is obfuscated in code and never shown in UI/logs.

**Tech Stack:** Kotlin, OkHttp, Hilt, DataStore, Jetpack Compose, JUnit4 (new JVM unit tests)

**Spec:** `docs/superpowers/specs/2026-08-10-52api-multi-platform-design.md`

## Global Constraints

- API key must be obfuscated (Base64 segments or equivalent); never log, never show in UI/errors
- Douyin local HTML parse path must remain the default; `/api/douyin` only on「云解析」select
- Unknown platforms must NOT fall through to Douyin local parse
- XHS without cookie: fail with「请先在「我的」中配置小红书 Cookie」before any network call
- Batch must share the same routing; per-item quality index + cloud-parse cache for Douyin videos
- Do not add Cookie tutorial page or server-side key delivery

## File Structure

| File | Responsibility |
| --- | --- |
| `app/src/main/java/com/douyin/downloader/data/model/LinkPlatform.kt` | Platform enum |
| `app/src/main/java/com/douyin/downloader/data/remote/LinkPlatformDetector.kt` | URL extract + platform detect |
| `app/src/main/java/com/douyin/downloader/data/remote/FiftyTwoApiClient.kt` | 52API HTTP + JSON → ContentInfo |
| `app/src/main/java/com/douyin/downloader/data/remote/FiftyTwoApiKey.kt` | Obfuscated key accessor only |
| `app/src/main/java/com/douyin/downloader/data/model/ContentInfo.kt` | Add `VideoQuality.isCloudParse` |
| `app/src/main/java/com/douyin/downloader/data/model/Exceptions.kt` | Unsupported platform / XHS cookie / API errors |
| `app/src/main/java/com/douyin/downloader/data/local/SettingsRepository.kt` | Persist `xhsCookie` |
| `app/src/main/java/com/douyin/downloader/data/repository/ContentRepository.kt` | Route by platform; append cloud placeholder for Douyin |
| `app/src/main/java/com/douyin/downloader/ui/home/HomeViewModel.kt` | Cloud lazy load (single+batch); per-item quality |
| `app/src/main/java/com/douyin/downloader/ui/home/HomeScreen.kt` | Cloud chip loading state |
| `app/src/main/java/com/douyin/downloader/ui/home/BatchScreen.kt` | Multi-platform copy + per-item quality chips |
| `app/src/main/java/com/douyin/downloader/ui/profile/ProfileScreen.kt` | XHS cookie card |
| `app/src/main/java/com/douyin/downloader/ui/profile/ProfileViewModel.kt` | Save/clear cookie |
| `app/src/test/java/.../LinkPlatformDetectorTest.kt` | Detector unit tests |
| `app/src/test/java/.../FiftyTwoApiMapperTest.kt` | Response mapping unit tests |
| `app/build.gradle.kts` | Add `testImplementation` JUnit |

---

### Task 1: Platform detector + unit tests

**Files:**
- Create: `app/src/main/java/com/douyin/downloader/data/model/LinkPlatform.kt`
- Create: `app/src/main/java/com/douyin/downloader/data/remote/LinkPlatformDetector.kt`
- Create: `app/src/test/java/com/douyin/downloader/data/remote/LinkPlatformDetectorTest.kt`
- Modify: `app/build.gradle.kts` (add JUnit)

**Interfaces:**
- Consumes: none
- Produces:
  - `enum class LinkPlatform { DOUYIN, SHIPINHAO, HAOKAN, WEISHI, XIAOHONGSHU, UNKNOWN }`
  - `object LinkPlatformDetector { fun extractUrl(raw: String): String?; fun detect(rawOrUrl: String): LinkPlatform }`

- [ ] **Step 1: Add JUnit test dependency**

In `app/build.gradle.kts` `dependencies { }` add:

```kotlin
testImplementation("junit:junit:4.13.2")
```

- [ ] **Step 2: Write failing detector tests**

```kotlin
package com.douyin.downloader.data.remote

import com.douyin.downloader.data.model.LinkPlatform
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkPlatformDetectorTest {
    @Test fun douyinShort() =
        assertEquals(LinkPlatform.DOUYIN, LinkPlatformDetector.detect("https://v.douyin.com/i2q93e3N/"))

    @Test fun shipinhao() =
        assertEquals(LinkPlatform.SHIPINHAO, LinkPlatformDetector.detect("https://weixin.qq.com/sph/AJfZ6d7Y37"))

    @Test fun haokan() =
        assertEquals(LinkPlatform.HAOKAN, LinkPlatformDetector.detect("https://haokan.hao123.com/v?vid=12080566475671209040"))

    @Test fun weishi() =
        assertEquals(LinkPlatform.WEISHI, LinkPlatformDetector.detect("https://isee.weishi.qq.com/ws/app-pages/share/index.html?id=xxx"))

    @Test fun xhs() =
        assertEquals(LinkPlatform.XIAOHONGSHU, LinkPlatformDetector.detect("http://xhslink.com/a/TarVNoFYclGeb"))

    @Test fun unknown() =
        assertEquals(LinkPlatform.UNKNOWN, LinkPlatformDetector.detect("https://www.example.com/video/1"))

    @Test fun extractFromShareText() {
        val raw = "复制打开抖音，看看【连蜜.】的作品 https://v.douyin.com/i2q93e3N/ 很棒"
        assertEquals("https://v.douyin.com/i2q93e3N/", LinkPlatformDetector.extractUrl(raw))
        assertEquals(LinkPlatform.DOUYIN, LinkPlatformDetector.detect(raw))
    }
}
```

- [ ] **Step 3: Run tests — expect FAIL (classes missing)**

Run: `./gradlew :app:testDebugUnitTest --tests com.douyin.downloader.data.remote.LinkPlatformDetectorTest`

Expected: compile failure / class not found

- [ ] **Step 4: Implement enum + detector**

`LinkPlatform.kt`:

```kotlin
package com.douyin.downloader.data.model

enum class LinkPlatform {
    DOUYIN, SHIPINHAO, HAOKAN, WEISHI, XIAOHONGSHU, UNKNOWN
}
```

`LinkPlatformDetector.kt`:

```kotlin
package com.douyin.downloader.data.remote

import com.douyin.downloader.data.model.LinkPlatform
import java.util.Locale

object LinkPlatformDetector {
    private val URL_REGEX = Regex("""https?://[A-Za-z0-9\-._~:/?#@!$&'()*+,;=%]+""")

    fun extractUrl(raw: String): String? {
        val m = URL_REGEX.find(raw.trim()) ?: return null
        return m.value.trimEnd(
            '，', '。', ',', '.', ';', '；', ':', '：',
            '!', '！', '?', '？', '、', ')', '）', ']', '】',
        )
    }

    fun detect(rawOrUrl: String): LinkPlatform {
        val url = (extractUrl(rawOrUrl) ?: rawOrUrl).lowercase(Locale.ROOT)
        return when {
            "v.douyin.com" in url || "iesdouyin.com" in url ||
                (".douyin.com" in url || url.contains("douyin.com/")) -> LinkPlatform.DOUYIN
            "channels.weixin.qq.com" in url ||
                ("weixin.qq.com" in url && "/sph" in url) -> LinkPlatform.SHIPINHAO
            "haokan.baidu.com" in url || "haokan.hao123.com" in url -> LinkPlatform.HAOKAN
            "weishi.qq.com" in url || "isee.weishi.qq.com" in url -> LinkPlatform.WEISHI
            "xiaohongshu.com" in url || "xhslink.com" in url ||
                Regex("""(^|//)xhs\.cn([/?#]|$)""").containsMatchIn(url) -> LinkPlatform.XIAOHONGSHU
            else -> LinkPlatform.UNKNOWN
        }
    }
}
```

- [ ] **Step 5: Run tests — expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests com.douyin.downloader.data.remote.LinkPlatformDetectorTest`

Expected: BUILD SUCCESSFUL, all tests PASS

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/com/douyin/downloader/data/model/LinkPlatform.kt app/src/main/java/com/douyin/downloader/data/remote/LinkPlatformDetector.kt app/src/test/java/com/douyin/downloader/data/remote/LinkPlatformDetectorTest.kt
git commit -m "feat: add link platform detector for multi-source paste"
```

---

### Task 2: Model markers + parse exceptions

**Files:**
- Modify: `app/src/main/java/com/douyin/downloader/data/model/ContentInfo.kt`
- Modify: `app/src/main/java/com/douyin/downloader/data/model/Exceptions.kt`

**Interfaces:**
- Consumes: none
- Produces:
  - `VideoQuality(..., isCloudParse: Boolean = false)`
  - `ParseException.UnsupportedPlatform`, `ParseException.XhsCookieRequired`, `ParseException.ApiFailed`

- [ ] **Step 1: Extend `VideoQuality`**

```kotlin
data class VideoQuality(
    val label: String,
    val bitRate: Int,
    val url: String,
    val format: String = "mp4",
    val isH265: Boolean = false,
    val isCloudParse: Boolean = false,
)
```

- [ ] **Step 2: Add parse exceptions**

In `ParseException` companion add codes `UNSUPPORTED_PLATFORM`, `XHS_COOKIE_REQUIRED`, `API_FAILED`, and classes:

```kotlin
class UnsupportedPlatform(
    message: String = "暂不支持该链接，目前支持抖音、视频号、好看、微视、小红书",
) : ParseException(message, UNSUPPORTED_PLATFORM)

class XhsCookieRequired(
    message: String = "请先在「我的」中配置小红书 Cookie",
) : ParseException(message, XHS_COOKIE_REQUIRED)

class ApiFailed(
    message: String = "解析失败，请稍后重试",
) : ParseException(message, API_FAILED)
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/douyin/downloader/data/model/ContentInfo.kt app/src/main/java/com/douyin/downloader/data/model/Exceptions.kt
git commit -m "feat: add cloud-parse quality flag and multi-platform parse errors"
```

---

### Task 3: Settings — XHS cookie persistence

**Files:**
- Modify: `app/src/main/java/com/douyin/downloader/data/local/SettingsRepository.kt`
- Modify: `app/src/main/java/com/douyin/downloader/ui/profile/ProfileViewModel.kt`
- Modify: `app/src/main/java/com/douyin/downloader/ui/profile/ProfileScreen.kt`

**Interfaces:**
- Consumes: existing DataStore settings pattern
- Produces:
  - `Settings.xhsCookie: String` (raw cookie text, may be empty)
  - `suspend fun setXhsCookie(value: String)`
  - `ProfileViewModel.onXhsCookieSaved(value: String)` / `onXhsCookieCleared()`

- [ ] **Step 1: Extend SettingsRepository**

Add to `Settings` data class: `val xhsCookie: String = ""`  
Add key `XHS_COOKIE = stringPreferencesKey("xhs_cookie")`  
Map in `flow`; implement:

```kotlin
suspend fun setXhsCookie(value: String) {
    context.dataStore.edit { it[Keys.XHS_COOKIE] = value.trim() }
}
```

- [ ] **Step 2: ProfileViewModel methods**

```kotlin
fun onXhsCookieSaved(value: String) {
    viewModelScope.launch { settingsRepository.setXhsCookie(value) }
}

fun onXhsCookieCleared() {
    viewModelScope.launch { settingsRepository.setXhsCookie("") }
}
```

- [ ] **Step 3: ProfileScreen UI card**

Insert a new `item { SettingCard(...) }` before「关于」, title「小红书 Cookie」, subtitle「解析小红书时需要；从浏览器登录小红书后复制 Cookie，原文粘贴即可」.

UI behavior:
- `OutlinedTextField` multi-line for draft text
- If `settings.xhsCookie.isNotBlank()`, show status text「已配置」（do not echo full cookie）
- Buttons:「保存」「清空」
- Never mention API key

- [ ] **Step 4: Manual check**

Install debug build, open「我的」, save a dummy cookie, kill app, reopen — status still「已配置」; clear — status gone.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/douyin/downloader/data/local/SettingsRepository.kt app/src/main/java/com/douyin/downloader/ui/profile/ProfileViewModel.kt app/src/main/java/com/douyin/downloader/ui/profile/ProfileScreen.kt
git commit -m "feat: add Xiaohongshu cookie setting on profile page"
```

---

### Task 4: FiftyTwoApiClient + mapper + obfuscated key

**Files:**
- Create: `app/src/main/java/com/douyin/downloader/data/remote/FiftyTwoApiKey.kt`
- Create: `app/src/main/java/com/douyin/downloader/data/remote/FiftyTwoApiClient.kt`
- Create: `app/src/test/java/com/douyin/downloader/data/remote/FiftyTwoApiMapperTest.kt`

**Interfaces:**
- Consumes: `OkHttpClient`, `LinkPlatform`, `ParseException`
- Produces:
  - `FiftyTwoApiKey.value: String` (decoded at runtime)
  - `suspend fun FiftyTwoApiClient.parse(platform: LinkPlatform, url: String, xhsCookieRaw: String = ""): ContentInfo`
  - Internal `fun mapDataToContentInfo(data: JSONObject, sourceUrl: String): ContentInfo` (visible for tests via `internal`)

- [ ] **Step 1: Write mapper unit tests (failing)**

```kotlin
package com.douyin.downloader.data.remote

import com.douyin.downloader.data.model.ContentInfo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FiftyTwoApiMapperTest {
    @Test
    fun mapsVideo() {
        val data = JSONObject(
            """{"work_title":"t","work_author":"a","work_cover":"c.jpg","work_type":"video","work_url":"https://cdn.example/v.mp4"}"""
        )
        val info = FiftyTwoApiClient.mapDataToContentInfo(data, "https://v.douyin.com/x/")
        assertTrue(info is ContentInfo.Video)
        info as ContentInfo.Video
        assertEquals("t", info.title)
        assertEquals("https://cdn.example/v.mp4", info.videoUrl)
    }

    @Test
    fun mapsImageGalleryFromArray() {
        val data = JSONObject(
            """{"work_title":"t","work_author":"a","work_cover":"c.jpg","work_type":"image","work_url":["https://cdn.example/1.jpg","https://cdn.example/2.jpg"],"music":{"url":"https://cdn.example/m.mp3"}}"""
        )
        val info = FiftyTwoApiClient.mapDataToContentInfo(data, "http://xhslink.com/a/x")
        assertTrue(info is ContentInfo.ImageGallery)
        info as ContentInfo.ImageGallery
        assertEquals(2, info.images.size)
        assertEquals("https://cdn.example/m.mp3", info.musicUrl)
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests com.douyin.downloader.data.remote.FiftyTwoApiMapperTest`

- [ ] **Step 3: Implement obfuscated key**

`FiftyTwoApiKey.kt` — do **not** store the full key as one string literal. Example approach:

```kotlin
package com.douyin.downloader.data.remote

import android.util.Base64

internal object FiftyTwoApiKey {
    // Base64 of the key, split so a simple strings dump is less obvious
    private val p1 = "OU5nbWhDMVYwcWxU"
    private val p2 = "bDRMTGVsUThqSm43WGs="

    val value: String
        get() = String(Base64.decode(p1 + p2, Base64.DEFAULT), Charsets.UTF_8)
}
```

(Verify at runtime in a temporary unit test that `value.length == 29` then delete that assertion test, or keep a length-only test that does not print the key.)

- [ ] **Step 4: Implement `FiftyTwoApiClient`**

```kotlin
@Singleton
class FiftyTwoApiClient @Inject constructor(
    private val client: OkHttpClient,
) {
    companion object {
        private const val BASE = "https://www.52api.cn/api/"

        internal fun mapDataToContentInfo(data: JSONObject, sourceUrl: String): ContentInfo {
            val title = data.optString("work_title").ifBlank { "未命名" }
            val author = data.optString("work_author").ifBlank { "未知作者" }
            val cover = data.optString("work_cover")
            val type = data.optString("work_type").lowercase()
            val id = Integer.toHexString(sourceUrl.hashCode())
            val musicUrl = data.optJSONObject("music")?.optString("url").orEmpty()

            val workUrl = data.opt("work_url")
            val imageList = mutableListOf<String>()
            var videoUrl = ""
            when (workUrl) {
                is JSONArray -> {
                    for (i in 0 until workUrl.length()) {
                        val s = workUrl.optString(i)
                        if (s.isNotBlank()) imageList.add(s)
                    }
                }
                is String -> {
                    if (type.contains("image") || type.contains("note") || type.contains("gallery")) {
                        if (workUrl.isNotBlank()) imageList.add(workUrl)
                    } else {
                        videoUrl = workUrl
                    }
                }
            }
            if (imageList.isNotEmpty() && videoUrl.isBlank()) {
                return ContentInfo.ImageGallery(
                    id = id, title = title, author = author, cover = cover.ifBlank { imageList.first() },
                    images = imageList, musicUrl = musicUrl, duration = 0,
                )
            }
            if (videoUrl.isBlank()) {
                throw ParseException.VideoUrlNotFound("未找到可下载的视频地址")
            }
            return ContentInfo.Video(
                id = id, title = title, author = author, cover = cover,
                videoUrl = videoUrl,
                qualities = listOf(VideoQuality(label = "默认", bitRate = 0, url = videoUrl)),
            )
        }
    }

    suspend fun parse(platform: LinkPlatform, url: String, xhsCookieRaw: String = ""): ContentInfo =
        withContext(Dispatchers.IO) {
            val path = when (platform) {
                LinkPlatform.DOUYIN -> "douyin"
                LinkPlatform.SHIPINHAO -> "sph"
                LinkPlatform.HAOKAN -> "haokan"
                LinkPlatform.WEISHI -> "weishi"
                LinkPlatform.XIAOHONGSHU -> "xhs"
                LinkPlatform.UNKNOWN -> throw ParseException.UnsupportedPlatform()
            }
            if (platform == LinkPlatform.XIAOHONGSHU && xhsCookieRaw.isBlank()) {
                throw ParseException.XhsCookieRequired()
            }
            val httpUrl = HttpUrl.Builder()
                .scheme("https").host("www.52api.cn")
                .addPathSegment("api").addPathSegment(path)
                .addQueryParameter("key", FiftyTwoApiKey.value)
                .addQueryParameter("url", url)
                .apply {
                    if (platform == LinkPlatform.XIAOHONGSHU) {
                        val b64 = Base64.encodeToString(
                            xhsCookieRaw.toByteArray(Charsets.UTF_8),
                            Base64.NO_WRAP,
                        )
                        addQueryParameter("cookie", b64)
                    }
                }.build()
            val request = Request.Builder().url(httpUrl).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw ParseException.ApiFailed("解析服务异常，HTTP ${resp.code}")
                }
                val root = JSONObject(body)
                val code = root.optInt("code", -1)
                if (code != 200) {
                    val msg = root.optString("msg").ifBlank { "解析失败" }
                    // Never append key/query
                    throw ParseException.ApiFailed(msg)
                }
                val data = root.optJSONObject("data")
                    ?: throw ParseException.ApiFailed("返回数据为空")
                mapDataToContentInfo(data, url)
            }
        }
}
```

Notes for implementer:
- Never `Log` the full request URL (contains key). If logging needed, log platform + host only.
- Use `okhttp3.HttpUrl` for query encoding.

- [ ] **Step 5: Run mapper tests — PASS**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/douyin/downloader/data/remote/FiftyTwoApiKey.kt app/src/main/java/com/douyin/downloader/data/remote/FiftyTwoApiClient.kt app/src/test/java/com/douyin/downloader/data/remote/FiftyTwoApiMapperTest.kt
git commit -m "feat: add 52API client with obfuscated key and content mapper"
```

---

### Task 5: ContentRepository routing + Douyin cloud placeholder

**Files:**
- Modify: `app/src/main/java/com/douyin/downloader/data/repository/ContentRepository.kt`

**Interfaces:**
- Consumes: `LinkPlatformDetector`, `FiftyTwoApiClient`, `SettingsRepository`, existing Douyin local parse
- Produces:
  - `parseUrl` routes by platform
  - `suspend fun resolveDouyinCloudParse(rawUrl: String): String` → video URL from `/api/douyin`
  - Douyin `ContentInfo.Video` / `Animated` qualities end with cloud placeholder

- [ ] **Step 1: Inject new deps**

```kotlin
@Singleton
class ContentRepository @Inject constructor(
    private val api: DouyinApi,
    private val parser: HtmlParser,
    private val animatedResolver: AnimatedVideoResolver,
    private val fiftyTwoApi: FiftyTwoApiClient,
    private val settingsRepository: SettingsRepository,
) {
```

- [ ] **Step 2: Rewrite `parseUrl` entry**

```kotlin
suspend fun parseUrl(rawUrl: String): ContentInfo {
    val extracted = LinkPlatformDetector.extractUrl(rawUrl)?.trim().orEmpty()
    if (extracted.isEmpty() && rawUrl.trim().isEmpty()) {
        throw ParseException.InvalidUrl("链接不能为空")
    }
    val url = extracted.ifEmpty { rawUrl.trim() }
    val platform = LinkPlatformDetector.detect(url)
    return when (platform) {
        LinkPlatform.DOUYIN -> parseDouyinLocal(url)
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
```

Move existing Douyin body into `parseDouyinLocal`, and after building `ContentInfo.Video` / `Animated`, append:

```kotlin
private fun withCloudPlaceholder(qualities: List<VideoQuality>): List<VideoQuality> =
    qualities + VideoQuality(label = "云解析", bitRate = -1, url = "", isCloudParse = true)

private fun ContentInfo.Video.withCloud(): ContentInfo.Video =
    copy(qualities = withCloudPlaceholder(qualities))

private fun ContentInfo.Animated.withCloud(): ContentInfo.Animated =
    copy(qualities = withCloudPlaceholder(qualities))
```

Apply `.withCloud()` on successful local Douyin video/animated results. Do **not** call 52API here.

- [ ] **Step 3: Add cloud resolve API**

```kotlin
suspend fun resolveDouyinCloudParse(rawUrl: String): String {
    val url = LinkPlatformDetector.extractUrl(rawUrl) ?: rawUrl.trim()
    val info = fiftyTwoApi.parse(LinkPlatform.DOUYIN, url)
    return when (info) {
        is ContentInfo.Video -> info.videoUrl
        is ContentInfo.Animated -> info.videoUrl
        else -> throw ParseException.VideoUrlNotFound("云解析未返回视频地址")
    }
}
```

Need `import kotlinx.coroutines.flow.first`.

- [ ] **Step 4: Smoke compile**

Run: `./gradlew :app:compileDebugKotlin`  
Expected: SUCCESS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/douyin/downloader/data/repository/ContentRepository.kt
git commit -m "feat: route parse by platform and append Douyin cloud-parse placeholder"
```

---

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

### Task 8: End-to-end verification + README touch

**Files:**
- Modify: `README.md` (功能表增加多平台 + 云解析说明；数据流一句)

- [ ] **Step 1: Checklist against spec**

1. Douyin local parse works; qualities include「云解析」; no `/api/douyin` until chip tap  
2. Cloud parse success downloads; failure reverts selection; second tap does not re-request  
3. 视频号 / 好看 / 微视 parse to downloadable video (live links)  
4. XHS without cookie → settings prompt; with cookie → video/images  
5. Unknown URL → UnsupportedPlatform, no Douyin HTML fetch  
6. Batch mixed platforms + Douyin cloud lazy load  
7. Grep APK/mapping / logcat: key string `9NgmhC1V0qlTl4LLelQ8jJn7Xk` must not appear as contiguous literal in source (`FiftyTwoApiKey` uses Base64 parts only)

- [ ] **Step 2: Update README features table briefly**

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: note multi-platform 52API parse and Douyin cloud quality"
```

---

## Spec coverage (self-review)

| Spec requirement | Task |
| --- | --- |
| Platform auto-detect | Task 1, 5 |
| Douyin local unchanged + cloud placeholder | Task 2, 5 |
| Cloud lazy on quality select | Task 6 |
| Non-Douyin 52API → ContentInfo | Task 4, 5 |
| XHS cookie in「我的」; prompt if missing | Task 3, 4, 5 |
| Key obfuscated, not in UI | Task 4, Global Constraints |
| Batch deep adapt + per-item cloud | Task 7 |
| Error copy table | Tasks 2/5/6/7 |
| No cookie tutorial / no server key | Non-goals — no task |

## Placeholder scan

No TBD/TODO left in task steps; mapper handles string/array `work_url`; batch download resolves cloud before enqueue.
