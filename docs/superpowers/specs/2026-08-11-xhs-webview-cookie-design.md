# 小红书 WebView 一键登录获取 Cookie 设计

**日期：** 2026-08-11  
**状态：** 已确认  
**方案：** 「我的」页「登录获取」→ WebView 登录 → 检测到 `web_session` 自动保存并关闭

## 背景

52API 小红书接口要求传入登录 Cookie（Base64）。当前仅支持用户从浏览器手动复制粘贴，体验差。需要在「圆圆解析」内提供 WebView 登录一键获取。

## 目标

1. 「我的」→「小红书 Cookie」增加「登录获取」入口。
2. 全屏 WebView 打开小红书网页；用户登录成功后自动读取 Cookie、写入 DataStore、关闭页面。
3. 保留手动粘贴 / 保存 / 清空。
4. 清空设置 Cookie 时同步清理 WebView 内小红书域 Cookie。

## 非目标

- 图文教程页
- 服务端代登 / Cookie 共享
- 自动刷新过期 Cookie（用户再次点「登录获取」即可）

## 登录成功判定

读取 `CookieManager.getCookie("https://www.xiaohongshu.com")`（及必要时 `https://www.xiaohongshu.com/`）：

- 存在非空的 `web_session=` 片段 → 视为已登录
- 兼容：若仅有 `a1=` 不足以判定登录（设备标识可能未登也有），**必须以 `web_session` 为准**

检测时机：`WebViewClient.onPageFinished` + 短间隔轮询（登录后可能 SPA 不整页刷新）。

## UI / 流程

1. 点「登录获取」→ 启动 `XhsLoginActivity`（全屏，带返回）
2. 加载 `https://www.xiaohongshu.com`
3. 检测到 `web_session` → 将完整 Cookie 原文写入 `SettingsRepository.setXhsCookie` → Toast「已获取小红书 Cookie」→ `finish()`
4. 用户按返回 → 取消，不改已有 Cookie
5. 清空：清空 DataStore + `CookieManager` 移除小红书相关 Cookie（避免旧会话）

设置页文案更新：说明可「登录获取」或手动粘贴；已配置仍只显示「已配置」。

## 技术

| 项 | 说明 |
| --- | --- |
| Activity | `XhsLoginActivity`，`@AndroidEntryPoint`，Manifest 注册（不 exported） |
| WebView | 启用 JS、DOM Storage、接受 Cookie；`setAcceptThirdPartyCookies` 在允许范围内开启 |
| 存储 | 复用现有 `xhsCookie` DataStore；解析侧不变（请求时 Base64） |
| 隐私 | Cookie 仅存本地；不日志打印完整 Cookie |

## 测试要点

1. 未登录打开页 → 不误保存  
2. 登录成功 → 自动关闭且「我的」显示已配置  
3. 解析小红书链接在有 Cookie 时可走 52API  
4. 清空后需重新登录；旧 WebView 会话不残留导致误判  
5. 手动粘贴路径仍可用  
