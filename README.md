# Vita

Vita 是一款本地优先、由用户自带云模型 API 的 Android 营养助手。

## 功能

- 从相机或相册选择 1–4 张图片；本机处理 EXIF 方向、移除元数据、缩放并重新编码后再发送给模型。
- 最近 7 天补录实际进餐时间；最近 90 天历史餐食搜索与安全复用。
- 今日营养进度、营养月历、食物明细和加密缩略图。
- 面向一般健康成人的本地确定性目标计算；年龄、身高、体重和资格筛查不发送给模型。
- 仅保留当前咨询会话；新建会话会删除旧会话和消息。
- 支持 OpenAI-compatible 与 Anthropic Messages 云接口，以及自定义 HTTPS 公网端点。

## 安全与隐私边界

- Vita 没有自建中转、账号系统、遥测或崩溃收集服务。所有模型请求直接发往用户配置的云模型厂商。
- 所有真实模型请求必须配置 API Key。端点和 Key 原子绑定，加密数据由 Android Keystore 保护；Key 不进入 WebView 或日志。
- 只允许无内嵌凭据的 HTTPS 公网端点。每次 DNS 解析均拒绝回环、局域网、CGNAT、链路本地、保留和组播地址；不跟随任何重定向。
- 上传图片最长边 3200px、JPEG 质量 90、单张不超过 8 MiB、整餐不超过 24 MiB。原图不落盘；确认后只保留加密 WebP 缩略图。
- Room 数据库使用 SQLCipher，不允许破坏性迁移回退。设置页可二次确认后清空全部本地数据。
- 详细说明见 [PRIVACY.md](PRIVACY.md) 和 [SECURITY.md](SECURITY.md)。

## 开发

要求 Node.js 22+、Android SDK 36 和 JDK 21。

```powershell
npm ci
npm test
npm run lint
npm run build
npx cap sync android
Set-Location android
./gradlew testDebugUnitTest lintDebug assembleDebug cyclonedxBom
Set-Location ..
npm run licenses
```

调试 APK 位于 `android/app/build/outputs/apk/debug/app-debug.apk`。正式发布由 GitHub `release` Environment 使用独立签名密钥构建；仓库不包含签名材料。

## 供应链

- npm 依赖由 `package-lock.json` 固定；Gradle wrapper 校验官方发行包 SHA-256。
- CI 固定第三方 Action 的完整提交 SHA，并执行 Web/Android 测试、Lint、npm audit、OSV、gitleaks、许可证清单和双侧 CycloneDX SBOM。
- 每个 Release 附带签名 APK、SHA-256、npm/Android SBOM、许可证清单和构建来源证明。

## 项目实现披露

本项目采用“人类主导需求、产品判断、评审和测试，结合生成式 AI 实现”的开发方式。所有代码、视觉资产和依赖都应接受来源、安全与许可证审计。

## 许可证

项目自有部分以 [Apache-2.0](LICENSE) 发布。第三方组件仍受各自许可证约束，见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。贡献须签署 DCO，见 [CONTRIBUTING.md](CONTRIBUTING.md)。
