# App3 隔振器顶升测力 Android

使用扭矩扳手逐个检测浮置板钢弹簧隔振器，离线采集扭矩与转角，完整保存、可视化并导出数据，为后续力值标定与临界分离点分析积累试验依据。

**完整项目背景、全部开发要求、当前状态和下一步任务都在[项目背景与开发要求](docs/项目背景与开发要求.md)。Claude 或其他 Agent 接手请先读这份文件。**

当前继续 Kotlin + Jetpack Compose，仅 Android。Windows 和更换语言留待后续。本机不安装开发环境，使用 GitHub Actions。

## 当前状态

功能基线 `b30f0e2` 对应 [Actions build 16](https://github.com/1837620622/app3-lift-wrench-android/actions/runs/42013640926)：65 项单元测试、API 28/35 各 3 项仪器测试、Lint、Debug/Release 编译、APK 版本及签名检查通过。调试包版本是 `0.2.0-debug.1016`。

**界面尚未通过用户验收。** 大字体横屏仍有数值裁切和绘图区过小的问题，接下来应按工业软件与 macOS/iOS 风格重新设计字体、布局和完整可视化。当前没有已验证的自动分离点算法，也未完成真实扳手现场验证。

## 使用与下载

1. 到 Actions 对应 run 的 Artifacts 下载应用 APK，安装 `app-debug.apk`，不要安装 androidTest 测试 APK。
2. 在设置中填写目标 Wi-Fi 名称、设备 IP/端口和板号/测点，先去系统连接设备 Wi-Fi。
3. 连接后自动接收真实数据并入库。每次连接建立独立会话，工具 SN 来自真实回传。
4. 结束采集后可导出当前或历史记录的完整 CSV。界面窗口不截断数据库和导出。
5. 软件结束采集不等于机械停止。旧应用不要直接卸载，先保留/导出旧数据。

Wi-Fi 名称可自定义并记住，不限制固定厂牌或 SSID。未标定或超标定范围不输出正式力值。当前优先 CSV，未实现原生 XLSX。

## 源码与协议

- `App3Android/`：Android Studio 工程，入口为 MainActivity → WrenchLiftApp → WorkerWorkbench。
- `最新协议.docx`：用户授权保留的厂家 C 协议原文。
- `docs/项目背景与开发要求.md`：唯一完整需求与接手文档。
- `AGENTS.md`：执行约束与提交规则。
- `.github/workflows/android.yml`：云端构建及模拟器矩阵。
- `tools/`：源码范围校验、云端签名准备与仪器测试脚本。

仓库只保留软件、授权协议和必要开发说明，不提交 CAD、真实数据、数据库、抓包、本机路径、密钥或环境缓存。当前版本的普通删除不清除历史提交。

## 构建规则

使用 Java 21、工程内 Gradle wrapper 和 Android SDK；CI 安装依赖，本机无需安装。已有环境的贡献者可在 App3Android 执行：

```shell
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease
```

版本基线 0.2.0，CI code=`1000+run_number`；调试 ID 带 `.debug`，主线调试签名由仓库 Secrets 提供，不能把调试签名当正式发行签名。APK 和测试证据放 Artifacts，不提交到源码。
