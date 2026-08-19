# 52API 多平台解析接入设计

**日期：** 2026-08-10  
**状态：** 已确认  
**方案：** 平台识别 + 专用 Client；抖音本地为主、云解析懒加载

## 背景

DYSave 当前仅通过本地 HTML 解析抖音分享链接。需要接入 [52API](https://www.52api.cn) 以下接口，并在粘贴时自动识别来源：

| 平台 | 文档 | 接口路径 |
| --- | --- | --- |
| 视频号 | https://www.52api.cn/doc/132 | `/api/sph` |
| 抖音 | https://www.52api.cn/doc/6 | `/api/douyin` |
| 好看视频 | https://www.52api.cn/doc/57 | `/api/haokan` |
| 微视 | https://www.52api.cn/doc/10 | `/api/weishi` |
| 小红书 | https://www.52api.cn/doc/42 | `/api/xhs` |

请求统一为 GET：`https://www.52api.cn/api/{path}?key=...&url=...`；小红书额外必填 `cookie`（Base64）。

抖音实测成功响应 `data` 字段示例（节选）：`work_title`、`work_author`、`work_cover`、`work_url`、`work_type`、`music`。

## 目标

1. 粘贴链接自动识别平台并解析。
2. 抖音：保持现有本地解析不变；清晰度列表增加「云解析」，**选中时再请求** 52API。
3. 视频号 / 好看 / 微视 / 小红书：识别后走对应 API，映射为现有 `ContentInfo`，下载/图集 UI 复用。
4. 单条解析与批量解析均深度适配上述逻辑；批量中仅抖音条目提供云解析懒加载。
5. API Key 内置且不暴露；小红书 Cookie 在「我的」设置页配置。

## 非目标

- Cookie 获取图文教程页
- API Key 服务端下发 / 动态更换（密钥不写死、启动后从自有服务器拉取或轮换——本次不做）
- 替换或删除现有抖音本地 HTML 解析链路

## 架构

| 组件 | 职责 |
| --- | --- |
| `LinkPlatform` | 枚举：`DOUYIN` / `SHIPINHAO` / `HAOKAN` / `WEISHI` / `XIAOHONGSHU` / `UNKNOWN` |
| `LinkPlatformDetector` | 从粘贴文本提取 URL，按域名/路径匹配平台 |
| `FiftyTwoApiClient` | 统一调用 52API；Key 混淆存储；错误信息不回显 Key |
| `ContentRepository` | 路由：抖音→本地解析 + 云解析占位；其他→52API→`ContentInfo` |
| `SettingsRepository` | 新增 `xhsCookie`（原文持久化，请求时 Base64） |
| `HomeViewModel` | 单条/批量：云解析懒加载、缓存、错误提示 |

### 平台识别规则（优先级从上到下）

- **抖音**：`v.douyin.com` / `douyin.com` / `iesdouyin.com`
- **视频号**：`weixin.qq.com/sph` / `channels.weixin.qq.com`
- **好看**：`haokan.baidu.com` / `haokan.hao123.com`
- **微视**：`weishi.qq.com` / `isee.weishi.qq.com`
- **小红书**：`xiaohongshu.com` / `xhslink.com` / `xhs.cn`
- **未知**：明确报错「暂不支持该链接…」，**不再**误走抖音本地解析

### API Key

- 内置进 App，使用简单混淆（分段拼接或 Base64 解码），避免源码与 APK 中出现完整明文常量。
- 不出现在设置页、UI、日志、错误文案中。

## 数据流

```
粘贴文本 / 批量每行
  → 提取 URL + LinkPlatformDetector
  → DOUYIN:
       现有 ContentRepository 本地解析
       qualities 末尾追加云解析占位（见下）
  → SHIPINHAO / HAOKAN / WEISHI / XIAOHONGSHU:
       FiftyTwoApiClient → ContentInfo.Video 或 ImageGallery
  → UNKNOWN: 明确报错
```

### 云解析占位标记

在 `VideoQuality` 增加明确字段（推荐 `isCloudParse: Boolean = false`），避免仅靠 label/空 url 判断：

- 占位：`VideoQuality(label = "云解析", url = "", isCloudParse = true)`
- 懒加载成功后原地更新（或不可变拷贝替换）该条目的 `url`，`isCloudParse` 仍为 true，便于 UI 识别与缓存判断（`url.isNotEmpty()` 表示已解析）。

### 抖音云解析（懒加载）

1. 首次解析**不**调用 `/api/douyin`。
2. 清晰度芯片末尾增加「云解析」。
3. 用户点选「云解析」→ 显示短暂 loading → `GET /api/douyin`。
4. 成功：将返回的 `work_url` 写入该 quality，并保持选中；同一次解析结果内缓存，重复点选不计费。
5. 失败：提示「云解析失败：{msg}，可改用本地清晰度」，选中回退到上一个本地清晰度。
6. 下载仍走现有 `getSelectedVideoUrl()`（及批量侧同等选路逻辑）。

### 非抖音字段映射

| 52API 字段 | ContentInfo |
| --- | --- |
| `work_title` | `title` |
| `work_author` | `author` |
| `work_cover` | `cover` |
| `work_url`（视频字符串） | `Video.videoUrl`；qualities 可空或单条「默认」 |
| `work_url`（图片数组）或无视频的图文 | `ImageGallery.images`（及 music 若有） |
| `work_type` | 辅助判断 video / 图文 |

`id`：可用 URL 哈希或从链接提取的平台侧 id；保证历史记录可区分即可。

### 小红书 Cookie

- 设置页「我的」新增「小红书 Cookie」卡片：多行输入、保存、清空；已配置显示「已配置」，不展示完整内容。
- DataStore 存原文；请求 `/api/xhs` 时再 Base64。
- **无 cookie**：不发请求，错误「请先在「我的」中配置小红书 Cookie」。
- **有 cookie**：支持视频与图文（映射为 `Video` / `ImageGallery`）。

## 批量解析

- 与单条共用 `ParseUrlUseCase` / `ContentRepository` 路由与平台识别。
- 每条独立成功/失败；小红书缺 cookie 时该条失败并带上述提示。
- 仅抖音成功条目的 `ContentInfo` 携带「云解析」占位。
- 批量 UI 需为抖音视频条目提供与单条一致的清晰度选择（含「云解析」懒加载）；每条维护自己的 `selectedQualityIndex` 与云解析缓存状态，互不共享。
- 批量一键下载时：若选中云解析且尚未拉取成功，先完成该条懒加载再入队；失败则跳过该条并提示，不影响其他条目。
- 不为批量单独做教程页或其他入口。

## UI

- **「我的」**：小红书 Cookie 配置（见上）；无 API Key 相关 UI。
- **主页单条**：抖音多清晰度 +「云解析」；非抖音视频直接「下载视频」；小红书图文复用图集多选/下载/合成。
- **批量**：列表展示各平台解析结果；抖音条目支持云解析切换（懒加载）。

## 错误处理

| 场景 | 提示 |
| --- | --- |
| 未知平台 | 暂不支持该链接，目前支持抖音、视频号、好看、微视、小红书 |
| 小红书无 cookie | 请先在「我的」中配置小红书 Cookie |
| 52API 业务失败 | 使用返回 `msg`（脱敏，不含 key） |
| 云解析失败 | 云解析失败：{msg}，可改用本地清晰度 |
| 网络异常 | 沿用现有网络错误文案 |

新增/复用 `ParseException` 子类时保持用户可读中文消息。

## 测试要点

1. 抖音本地解析仍成功；清晰度含「云解析」；未点选时不产生 52API 抖音调用。
2. 点「云解析」成功可下载；失败可回退本地清晰度；同会话重复点选不二次请求。
3. 视频号 / 好看 / 微视链接解析出可下载视频。
4. 小红书无 cookie 提示配置；配置后视频与图文均可解析。
5. 未知链接报错且不走抖音本地解析。
6. 批量混合多平台：各自成功/失败独立；抖音条目云解析懒加载可用。
7. UI/日志中不出现 API Key 明文。

## 实现触点（预期文件）

- 新增：`LinkPlatform.kt` / `LinkPlatformDetector.kt`、`FiftyTwoApiClient.kt`（或等价命名）
- 修改：`ContentRepository.kt`、`ContentInfo.kt`（`VideoQuality.isCloudParse`）、`HomeViewModel.kt`、`HomeScreen.kt`、`BatchScreen.kt`、`SettingsRepository.kt`、`ProfileScreen.kt` / `ProfileViewModel.kt`、`Exceptions.kt`、`AppModule.kt`（如需注入）
- 文档：本 spec；实现计划另见 `docs/superpowers/plans/`
