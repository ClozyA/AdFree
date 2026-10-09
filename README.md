# AdFree

基于 LSPosed Modern API 102 的 Android 模块，包名为 `xyz.fearr.adfree`。界面使用 Compose 与 [Miuix](https://github.com/compose-miuix-ui/miuix)，提供连接概览、适配应用与环境信息工具，支持系统深浅色。

[下载最新版本](https://github.com/ClozyA/AdFree/releases/latest)

## 安装

1. 安装 Release APK；Debug APK 用于排查问题。
2. 在支持 API 102 的 LSPosed 中启用模块。随后可在 AdFree 的应用列表中管理两个目标应用的作用域；申请加入作用域需经框架授权。
3. 完全退出并重新打开目标应用，在框架日志中检查 `AdFree` 的加载记录。

从旧包名 `dev.adfree.module` 迁移时，请停用旧模块，避免两版同时注入。框架服务连接只表示模块应用能访问框架，目标进程是否注入仍需查看框架日志。

## 当前适配

| 应用 | 包名 | 适配版本 |
|---|---|---|
| 小步点 | `run.xbud.android` | 2.6.8 |
| 5E 对战平台 | `com.fiveplay` | 7.2.5 |

界面读取当前用户下两个目标应用的真实安装版本；未安装时显示提示。版本名或版本号与已验证版本不同会提示，但继续尝试注入和安装规则。无法定位的类或方法会跳过相应规则，兼容效果需实际验证。

现有规则包含开屏、插屏及已识别 SDK 的广告页面、控件和加载入口，并包含激励广告，不保留奖励流程。强制拦截可能影响启动检查、登录或奖励入口；不承诺阻断全部广告网络请求。

用户已反馈两个应用的现有适配均正常；其他版本和新增的作用域控制仍需真机验证。作用域开关展示框架实际结果，不是本地保存的开关。未连接时禁用操作，修改后需完全退出并重启目标应用。

## 开发

在 Android Studio 中打开项目。构建工具为 Android SDK 37、Build Tools 37.0.0、AGP 9.4.1、Gradle 9.6.0、Kotlin 2.4.20、JDK 17，最低 Android 8.0 / API 26。

项目命令使用 PowerShell 7（`pwsh`）：

```powershell
$env:JAVA_HOME = '你的 JDK 17 目录'
$env:ANDROID_HOME = '你的 Android SDK 目录'
./gradlew.ps1 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

默认 Debug 使用本机 Android 调试证书。签名配置齐全时，Debug 与 Release 均使用项目发布证书，便于两种构建相互切换。

## 本地 Release 签名

复制 `signing.local.properties.example` 为 `signing.local.properties`，填写私有签名配置；也可使用对应的环境变量。配置与私钥均不进入 Git。

```powershell
./gradlew.ps1 :app:assembleRelease :app:assembleDebug
./scripts/package.ps1 -Version 0.10.0
```

产物位于 `dist/`，包含 Release、Debug 和 `SHA256SUMS`。Release 启用 R8 和资源裁剪，缺少签名配置会阻止发布打包。

## 自动发版

GitHub Secrets 需配置：

- `ADFREE_KEYSTORE_BASE64`：发布 keystore 的 Base64 内容。
- `ADFREE_KEYSTORE_PASSWORD`、`ADFREE_KEY_ALIAS`、`ADFREE_KEY_PASSWORD`：签名配置。

更新 `app/build.gradle.kts` 的 `versionCode` 和 `versionName`，可在 `docs/releases/<版本>.md` 编写说明，然后推送匹配的版本标签：

```powershell
git tag v0.10.0
git push origin v0.10.0
```

工作流在 Windows / PowerShell 7 中运行单元测试、lint、两种构建、签名验证和 SHA-256 计算，然后发布两种 APK。Android 17 的 SDK 平台包名为 `platforms;android-37.0`。也支持在 GitHub 创建已发布的 Release，或从主分支手动运行工作流并输入已有版本标签。

发布签名必须长期保留，避免后续版本无法覆盖升级。之前使用默认调试证书安装的同包名 APK，与正式发布证书不同，首次切换可能需要卸载旧调试版。

## 代码

- `app/src/main/kotlin/xyz/fearr/adfree/MainActivity.kt`：Miuix 界面。
- `app/src/main/java/xyz/fearr/adfree/ModuleApplication.java`：框架服务监听。
- `app/src/main/java/xyz/fearr/adfree/xposed/`：模块入口及应用适配。
- `app/src/main/resources/META-INF/xposed/`：API 要求、入口和作用域。
- `app/src/test/`：规则与 SDK 合同的单元测试夹具。
