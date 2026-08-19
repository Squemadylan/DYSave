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

