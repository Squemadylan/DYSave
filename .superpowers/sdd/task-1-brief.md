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

