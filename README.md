# LocoWiki Flash Recall Android

> Copyright (c) 2026 QuJindai. All rights reserved.  
> This repository is publicly visible but is not open source.

独立公开 Android 项目，用于本地会议记录、会前 SELF 声纹登记和本地事实召回。它不是 `QuJindai/agent-localwiki` 的镜像，与 `QuJindai/locowiki-android-public-ci` 为平行项目，并且不依赖这两个仓库进行构建或运行。

## v0.4.0-alpha.1

- application ID：`com.qujindai.locowiki.flashrecall`
- ARM64 Debug 签名测试 APK
- 会前完成三段 SELF 声纹登记
- 正式路径每段创建独立 `AudioRecord`
- Debug 云测以应用沙箱 PCM 验证 `0/3 → 1/3 → 2/3 → 3/3`
- 不申请 `android.permission.INTERNET`

## 权利

公开可见不代表开源或授权。除 GitHub 平台运行公开仓库所必需的查看、展示、平台内 fork 和技术复制外，未经 QuJindai 事先书面许可，不得复制、修改、创建衍生作品、分发、转授权、出售或商用。第三方组件继续受各自许可证约束。
