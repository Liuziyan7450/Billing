# Billing (简记账)

一个基于 **Jetpack Compose + Material 3** 的离线记账 App，可直接导入 Android Studio 构建 APK。

## 技术栈
- Android Gradle Plugin 8.2.2
- Gradle 8.7
- Kotlin 2.0.21
- compileSdk / targetSdk = 36（Android 16）
- Jetpack Compose + Material 3
- Room 本地数据库
- JSON 导入/导出（kotlinx.serialization）

## 核心功能
- 首页快速记账（底部弹出输入）
- 当日账单列表
- 账单分类预置 + 新增/编辑/删除
- 分析页 3 个 Tab：概览、趋势、分类占比
- 全离线存储，支持 JSON 备份与恢复


> 说明：仓库未提交 `gradle-wrapper.jar`（避免二进制文件导致的 PR 平台限制）。
> 你可以在 Android Studio 打开项目后自动生成，或本地执行 `gradle wrapper --gradle-version 8.7`。

## 构建
```bash
./gradlew assembleDebug
```
