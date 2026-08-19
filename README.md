# DYSave

> 圆圆解析，无水印下载各平台内容 Android 客户端



![License](https://img.shields.io/badge/license-MIT-blue)   ![Min SDK](https://img.shields.io/badge/min%20SDK-26%20(Android%208.0)-brightgreen)  ![Target SDK](https://img.shields.io/badge/target%20SDK-35-blue)

---

## 功能

| 功能               | 说明                           |
| ---------------- | ---------------------------- |
| **多平台解析**        | 自动识别抖音、视频号、好看、微视、小红书链接；非抖音走 52API，小红书需在「我的」配置 Cookie |
| **视频下载**         | 解析分享链接，提取无水印视频，流式下载到系统相册；抖音可选「云解析」清晰度（点击后懒加载 52API） |
| **图文下载**         | 解析图文帖子，逐张保存图片到相册（不压缩）        |
| **动图下载**         | 识别动图类型，自动合并背景音乐              |
| **合成视频**         | 将多张图片 + 背景音乐合成为视频（FFmpegKit） |
| **下载队列**         | 后台并发下载（最多 2 任务），实时进度显示       |
| **下载记录**         | 持久化存储下载历史，应用重启后可查看           |
| **历史记录**         | 保存解析历史，点击可快速回填               |
| **Share Intent** | 从抖音 App 直接分享链接到本应用           |

---

## Cookie 配置（抖音 / 小红书）

解析抖音、小红书需要登录态 Cookie，在「我的」页面可配置。两种方式：

| 平台 | 作用 | 获取方式 |
| --- | --- | --- |
| **抖音** | 提高视频解析成功率（缓解 WAF / 限流） | ① Root 一键获取（推荐，需 root）② 登录获取（WebView 网页登录）③ 手动粘贴 |
| **小红书** | 解析小红书笔记必需 | ① Root 一键获取（需先在 App 内登录一次）② 登录获取 ③ 手动粘贴 |

- **Root 一键获取**：通过 `su -M`（mount-master，绕过 Android 应用数据隔离）读取目标 App 的 WebView Cookie 数据库。
  - 抖音：直接从抖音 App 提取登录态（无需在 DYSave 内再登录）。
  - 小红书：小红书 App 本身不暴露 Web Cookie，需先在 DYSave 内用「登录获取」完成一次网页登录，会话 Cookie 才会落到 DYSave 自身 WebView，之后「Root 一键获取」即可捕获。
- **登录获取**：内置 WebView 打开对应登录页（抖音：`https://www.douyin.com/passport/sso/`；小红书：小红书网页登录），登录成功自动写入。
- 要求设备已授予 root 权限（Magisk / APatch / KernelSU）。

---

## 技术栈

| 模块   | 技术                                      |
| ---- | --------------------------------------- |
| 语言   | Kotlin                                  |
| UI   | Jetpack Compose + Material 3            |
| 架构   | MVVM (ViewModel + Repository + UseCase) |
| 依赖注入 | Hilt                                    |
| 网络   | OkHttp                                  |
| 图片加载 | Coil                                    |
| 视频播放 | Media3 ExoPlayer                        |
| 视频合成 | FFmpegKit                               |
| 数据库  | Room                                    |
| 后台任务 | WorkManager                             |
| 文件存储 | MediaStore (Android 10+)                |

### 依赖版本

| 依赖                    | 版本         |
| --------------------- | ---------- |
| Android Gradle Plugin | 8.7.3      |
| Kotlin                | 2.1.10     |
| Compose BOM           | 2025.05.00 |
| Hilt                  | 2.56.1     |
| Room                  | 2.7.1      |
| OkHttp                | 4.12.0     |
| Coil                  | 2.7.0      |
| Media3 ExoPlayer      | 1.6.0      |
| FFmpegKit             | 6.1.1      |
| WorkManager           | 2.10.1     |

---

## 系统要求

- Android 8.0 (API 26) 及以上
- 仅支持 `arm64-v8a` 架构

---

## 构建

```bash
# 使用 Android Studio 自带的 JDK
export JAVA_HOME="/path/to/Android Studio/jbr"

# 编译 Debug APK
./gradlew :app:assembleDebug

# 安装到设备
./gradlew :app:installDebug
```

---

## 项目结构

```
app/src/main/java/com/douyin/downloader/
├── App.kt                          # Application 入口 (Hilt)
├── MainActivity.kt                 # 单 Activity，处理 Share Intent
│
├── data/
│   ├── local/                      # Room 数据库、DAO、Entity
│   │   ├── AppDatabase.kt
│   │   ├── HistoryDao.kt
│   │   ├── HistoryEntity.kt
│   │   ├── DownloadTaskDao.kt
│   │   └── DownloadTaskEntity.kt
│   ├── model/                      # 数据模型
│   │   └── ContentInfo.kt          # Video / ImageGallery / Animated 三种类型
│   ├── remote/                     # 网络请求、HTML 解析、Cookie 获取
│   │   ├── DouyinApi.kt
│   │   ├── HtmlParser.kt
│   │   ├── AnimatedVideoResolver.kt
│   │   ├── DouyinCookieHelper.kt    # 抖音登录态判断
│   │   ├── XhsCookieHelper.kt       # 小红书登录态判断
│   │   └── RootCookieFetcher.kt     # Root(su -M) 读取目标 App WebView Cookie
│   └── repository/                 # 数据仓库
│       ├── ContentRepository.kt
│       └── DownloadRepository.kt
│
├── di/                             # Hilt 依赖注入模块
│   └── AppModule.kt
│
├── domain/usecase/                 # 业务用例
│   ├── ParseUrlUseCase.kt
│   ├── DownloadVideoUseCase.kt
│   ├── DownloadImagesUseCase.kt
│   └── SynthesizeVideoUseCase.kt
│
├── ui/
│   ├── home/                      # 解析主页面
│   ├── downloads/                  # 下载中心页面
│   ├── history/                   # 历史记录页面
│   ├── navigation/                # 底部导航框架
│   ├── components/                # 通用组件（VideoPlayer 等）
│   └── theme/                    # Material 3 主题
│
└── worker/                         # WorkManager 后台下载任务
    └── DownloadWorker.kt
```

### 数据流

```
用户粘贴链接
    ↓
ParseUrlUseCase → ContentRepository.parseUrl()
    ↓
LinkPlatformDetector 识别平台 → 抖音本地 HTML 解析；其他平台 52API；未知链接 UnsupportedPlatform
    ↓
返回 ContentInfo (Video / ImageGallery / Animated)；抖音视频/动图含「云解析」占位清晰度
    ↓
用户触发下载
    ↓
DownloadVideoUseCase / DownloadImagesUseCase
    ↓
DownloadRepository → MediaStore 保存到相册
    ↓
WorkManager 后台任务 + Room 记录下载历史
```

---

## 内容类型

`ContentInfo` 是一个 sealed class，包含三种内容类型：

- **`Video`** — 普通视频，含 `videoUrl`
- **`ImageGallery`** — 图文帖，含 `images` 列表、`musicUrl`、`duration`，可合成为视频
- **`Animated`** — 动图（同时有图片序列和视频），含 `videoUrl` 优先下载

---

## 许可证

[MIT License](LICENSE)