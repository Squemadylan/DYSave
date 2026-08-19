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

