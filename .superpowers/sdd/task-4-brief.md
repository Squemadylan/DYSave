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

