
## DYSave Shizuku 自动获取 Cookie 方案（已验证不可行）
- 目标：通过 Shizuku run-as 读取抖音/小红书 App 的 SharedPrefs Cookie
- 结论：正式 App 非 debuggable，`run-as` 被系统禁止
- 设备现状：无 Root 权限
- 保留代码：ShizukuCookieFetcher.kt 已添加重试逻辑，换 Root 设备后可重新测试
- 当前可用方案：WebView 一键登录 + 手动粘贴 Cookie

## 模拟器调试环境
- AVD: Pixel_6_Android_14 (emulator-5554)
- 系统：Android 14 (api 34), Google APIs, x86_64
- Root：已启用，`adb shell "su 0 <cmd>"` 可直接获取 root
- GPU：AMD Radeon 780M 硬件加速（Vulkan）
- cmdline-tools 已安装到 SDK
