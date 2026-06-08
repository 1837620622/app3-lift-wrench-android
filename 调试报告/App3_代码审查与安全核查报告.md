# App3 代码审查与安全核查报告

## 核查范围

- 仓库：`1837620622/app3-lift-wrench-android`
- 本地目录：`/Users/chuankangkk/Downloads/隔振器顶升开发软件`
- Android 工程：`App3Android`
- 核查重点：权限、备份、明文网络、日志、原始 HEX、协议输入解析、危险控制命令、Git 泄漏、CI 状态。

## CodeRabbit 审查状态

CodeRabbit CLI 已安装并登录：

```text
coderabbit --version: 0.5.2
coderabbit auth status --agent: authenticated
```

尝试命令：

```bash
coderabbit review --agent -c AGENTS.md
coderabbit review --agent -t committed -c AGENTS.md
```

结果：

```text
Review failed: No files found for review
```

原因判断：当前 `main` 分支已同步远端，没有未提交或相对 base 的可审查 diff。后续产生实际代码改动后，再对 uncommitted 或 PR diff 运行 CodeRabbit。

本轮安全修复产生真实未提交 diff 后，已重新运行：

```bash
coderabbit review --agent -t uncommitted -c AGENTS.md
```

CodeRabbit raised 2 issues，均为 minor：

- `AndroidManifest.xml`：确认 `android:allowBackup="false"` 是否为故意。结论：故意关闭。应用保存现场历史、原始 HEX、设备结果和未来标定参数，不应默认进入系统云备份；后续如需迁机，应做显式导出/导入。
- `AndroidManifest.xml`：确认 `ACCESS_COARSE_LOCATION` 是否必要。结论：保留。项目 `minSdk=26`，仍读取 SSID 和旧版 Wi-Fi 信息，且 Android lint 对 `ACCESS_FINE_LOCATION` 要求同时声明 `ACCESS_COARSE_LOCATION`。

## GitHub Actions 状态

已确认：

- 仓库未归档、未禁用。
- 仓库已按用户要求从私有改为公开：`https://github.com/1837620622/app3-lift-wrench-android`。
- Actions 权限开启，`allowed_actions=all`。
- 6 月账单 API 未显示 Actions 净欠费，`netAmount=0`。
- Android CI 和最小 `Actions Smoke` 都在创建 job 前 `startup_failure`。

公开后重新手动触发：

```text
Actions Smoke: https://github.com/1837620622/app3-lift-wrench-android/actions/runs/27125690625
Android CI:    https://github.com/1837620622/app3-lift-wrench-android/actions/runs/27125692453
```

现象：

```text
job 已创建，但 runner_id=0，runner_name 为空，steps 为空，log not found
```

结论：当前不是 Gradle 或 Android workflow 脚本失败，而是 GitHub 账号、组织策略、runner 分配、支付/额度或 GitHub runner 启动层问题。账号恢复后先运行 `Actions Smoke`，通过后再运行 `Android CI`。

已补充 Android CI artifact 上传步骤；runner 恢复后，CI 会执行单元测试、lint、debug APK 构建，并上传 `app3-wrench-lift-debug-apk`。

## Android lint

离线 lint 初次失败原因：

```text
No cached version of com.android.tools.lint:lint-gradle:32.2.1 available for offline mode
```

已确认 Gradle 缓存走 SSD：

```text
~/.gradle -> /Volumes/256G/AndroidDev/gradle-user-home
GRADLE_USER_HOME=/Volumes/256G/AndroidDev/gradle-user-home
```

联网补齐 lint 依赖后运行：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
GRADLE_USER_HOME="/Volumes/256G/AndroidDev/gradle-user-home" \
./gradlew lintDebug --no-daemon --console=plain \
  -Papp3.android.buildRoot="/Volumes/256G/AndroidDev/app3-android-build"
```

发现 1 个错误：

```text
ACCESS_FINE_LOCATION 必须同时声明 ACCESS_COARSE_LOCATION
```

已修复：

- 补 `ACCESS_COARSE_LOCATION`。
- `NEARBY_WIFI_DEVICES` 增加 `android:usesPermissionFlags="neverForLocation"`。
- 关闭 `android:allowBackup`，避免现场数据库和原始 HEX 被系统自动备份。
- 增加 Android 12+ `dataExtractionRules` 和旧版 `fullBackupContent`，禁止云备份和设备迁移备份现场数据。

剩余 lint warning：

- `targetSdk=35` 不是最新值。
- 依赖库有新版本。
- 横屏锁定在 Android 16 起可能被系统弱化。

这些 warning 暂不直接升级，避免在未真机联调前引入 Android 版本兼容变量。

## 安全核查结果

### 已修复

- 系统自动备份：已从 `allowBackup=true` 改为 `allowBackup=false`。
- Android 12+ Wi-Fi 权限：已补粗略定位权限，避免运行时授权异常。
- Wi-Fi 设备权限：标记不用于定位，降低权限解释风险。
- 正式模拟入口：主源码未发现 `Fake`、`Simulation`、`模拟采集`、`startSimulation`、`stopSimulation`。
- 设计预览：Compose Preview 中设备编号从 `APP3-MOCK` 改为 `APP3-PREVIEW`，避免误解为正式数据源。
- TCP 心跳在断线后停止，避免后台协程继续发送导致异常。
- 最终结果帧先保存再记录确认发送状态，确认失败不会丢失现场结果。

### 已确认

- 没有发现硬编码 GitHub token、API key、password 明文。
- 旧桌面版目录被 `.gitignore` 排除，没有进入新仓库跟踪。
- 协议解析有帧头、长度、校验和和缓冲上限，异常数据不会无限增长。
- 默认不自动发送正转、反转、参数设置；高风险动作在 UI 有二次确认。
- 原始 HEX 保存是项目需求，用于现场追溯和协议调试。

### 仍需真机验证

- Android 运行时权限弹窗和 Wi-Fi 名称读取。
- 真实手机连接扳手 Wi-Fi 后，TCP 连接、心跳、SN 查询和最终结果回传。
- 横屏大屏在不同手机尺寸下的文字、按钮和曲线可读性。
- CSV/Excel 导出权限和保存位置。

## 下一步建议

1. 先在 GitHub 网页修复 Actions 账号侧预算或支付限制。
2. 账号恢复后运行 `Actions Smoke`，通过后再运行 `Android CI`。
3. 产生下一轮真实代码 diff 后，重新运行 CodeRabbit。
4. 真机到手后先只做连接、心跳、SN、电量、最终结果帧验证，再开放正反转控制。
