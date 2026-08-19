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

