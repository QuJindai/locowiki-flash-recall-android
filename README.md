# LocoWiki Flash Recall Android

> Copyright (c) 2026 QuJindai. All rights reserved.  
> This repository is publicly visible but is not open source.

这是一个独立的 Android 项目，用于 LocoWiki Flash Recall 的本地会议记录、会前 SELF 声纹登记和本地事实召回。

本仓库：

- 不是 `QuJindai/agent-localwiki` 的镜像；
- 与 `QuJindai/locowiki-android-public-ci` 是平行项目；
- 构建和发布不依赖上述两个仓库；
- 使用全新的 Git 历史。

## 当前版本

首个公开测试版本目标为 `v0.4.0-alpha.1`：

- Android application ID：`com.qujindai.locowiki.flashrecall`
- ARM64 Debug 签名测试 APK
- 会前完成三段 SELF 声纹登记
- 正式应用使用手机真实麦克风
- 云端模拟器使用仅 Debug 可启用的受限沙箱 PCM 验证三段状态机
- APK 不申请 `android.permission.INTERNET`

## 权利声明

公开可见不代表开源，也不构成许可。除 GitHub 平台运行公开仓库所必需的查看、展示、平台内 fork 和技术复制外，未经 QuJindai 事先书面许可，不得复制、修改、创建衍生作品、分发、转授权、出售或用于商业目的。

第三方库、模型和工具继续受各自许可证约束。详见 `COPYRIGHT.md`、`NOTICE-NO-LICENSE.md` 和 `docs/third-party-notices.md`。

## 状态

仓库正在建立净化源码、模型锁定、免费公共 CI、三段声纹模拟器门禁和首个 Pre-release。
