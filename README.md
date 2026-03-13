# 简记账（Billing）

一个离线优先（Offline-first）的 Android 记账 App，基于 **Jetpack Compose + Material 3 + Room**。

> 目标：在本地完成快速记账、分类预算管理与统计分析，并支持 JSON 全量数据备份/恢复。

---

## 1. 当前能力

### 记账与首页
- 快速新增支出/收入（底部弹层 + 数字键盘）。
- 当天记录列表展示，可删除单条记录。
- 收入与支出分类分离。

### 分类管理
- 分类页分为：支出分类 / 收入分类。
- 支出分类支持预算上限（可选）。
- 显示本月预算进度（已支出与进度条）。
- 若分类已有记录，禁止直接删除（仅提示）。

### 统计分析
- 概览：当日支出、本月支出、本月收入、本月结余。
- 趋势：日历视图（可点日期看当日明细），并显示当日支出金额。
- 分类：按时间范围统计，支持收入/支出构成切换，饼图展示占比。

### 数据备份与恢复（JSON）
- 顶栏「数据操作」菜单支持导出/导入 JSON。
- **导出为全量数据**（分类 + 记录 + 元信息）。
- **导入为合并策略**：
  - 不清空本地已有数据；
  - 分类按稳定 UID（或类型+名称）合并；
  - 记录按稳定 UID 去重后追加；
  - 避免“导入后覆盖当天数据”的问题。

---

## 2. 技术栈

- Kotlin 2.0.21
- Android Gradle Plugin 8.2.2
- Gradle 8.7+
- Jetpack Compose + Material 3
- Room
- kotlinx.serialization
- Kizitonwose Calendar（趋势日历）
- MPAndroidChart（饼图）

---

## 3. 工程结构（核心）

- `app/src/main/java/com/example/billing/MainActivity.kt`：主界面与主要 Compose 页面。
- `app/src/main/java/com/example/billing/data/`：Room 实体、DAO、Repository。
- `app/src/main/java/com/example/billing/viewmodel/BillingViewModel.kt`：状态管理与业务编排。
- `app/src/main/java/com/example/billing/theme/Theme.kt`：主题。
- `docs/需求文档.md`：需求与验收说明。

---

## 4. 导入/导出 JSON 说明（重要）

当前导出文件包含：
- `metadata`：`exportId`、`exportedAt`、`timezone`、`schemaVersion`。
- `categories`：包含 `uid`、`budgetLimit` 等字段。
- `records`：包含 `uid`、`dateLabel`、`categoryUid` 等字段。

导入逻辑：
1. 先合并分类（按 `uid`，缺失则按“类型+名称”稳定键）；
2. 再导入记录（按 `uid` 去重）；
3. 不会清空本地库。

这使得跨设备多次导入也能尽量避免重复与覆盖。

---

## 5. 构建

```bash
./gradlew assembleDebug
```

如果本仓库未提交 `gradle-wrapper.jar`，`gradlew` 会回退到系统 `gradle`。
建议优先使用 Android Studio 打开项目并完成 Sync 后构建。

---

## 6. 已知环境限制

在当前在线容器环境中，可能无法访问 Google Maven，导致 AGP 插件解析失败。
这属于环境网络限制，不影响项目在可联网 Android 开发环境中的正常构建。
