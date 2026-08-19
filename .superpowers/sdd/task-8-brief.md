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
