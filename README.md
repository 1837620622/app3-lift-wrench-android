# WiFi 扳手

## 项目背景（先读这里）

**不是开发一把新扳手。** 现场用厂家定制的工业扭矩扳手（Wi‑Fi + TCP/C 协议），对浮置板下大量**钢弹簧隔振器**做顶升测力与**调频**。厂家扳手**给不出可靠「平衡点」**，协议也没有该字段——所以要先把**实时扭矩可视化、远程控制扳手、全量数据与带原生图表的 Excel 导出**做扎实，攒够几百～几千点后，**下一步再做平衡点算法并内嵌**。

更完整的项目背景：[docs/项目背景.md](docs/项目背景.md)  
**UI 设计规范（交接权威，写死）：** [docs/设计规范.md](docs/设计规范.md)（视觉由产品方设计；docs 以文字规范为准）  
二次开发接手：[docs/开发接手说明.md](docs/开发接手说明.md)  
协议实现细节 / Excel / 阶段计划：[docs/界面与导出优化方向.md](docs/界面与导出优化方向.md)  
完整工程约束：[docs/项目背景与开发要求.md](docs/项目背景与开发要求.md)

当前继续 **Kotlin + Jetpack Compose**，仅 Android。本机可不装开发环境，使用 GitHub Actions。**产品大改代码仍冻结**，以文档交接为准；用户还将继续补充需求。

**交付次序：** ① 先按已定逻辑产出高级渲染图与宣传动态（视觉基准）；② 再按同一视觉改软件，确保渲染、视频、软件一体。

## 权利与供应

- **软件著作权**：本 App 及本仓库原创软件部分归 **传康Kk**（GitHub `1837620622`）所有。厂家扳手硬件与协议原文权利归设备厂家。
- **供应方式**：当前为**免费供应**，不是付费商品或订阅服务；商务变更以著作权人书面说明为准。
- 推送、公开发布或改名商用须经著作权人明确授权。

## 当前状态

功能基线 `b30f0e2` 对应 [Actions build 16](https://github.com/1837620622/wifi-wrench-android/actions/runs/42013640926)：65 项单元测试、API 28/35 仪器测试、Lint、Debug/Release 与签名检查通过。调试包曾用 `0.2.0-debug.1016`。

**界面尚未用户验收。** 已锁定方向：工业 + Mac/iOS 观感、横屏单屏无竖滚、Wi‑Fi 优先、**正式界面纳入远程控制**、实时扭矩–转角可读（按转角跨度自适应窗口）、**xlsx 为主且含原生 Excel 图**（设置可选 CSV）。算法为数据充足后的 **Phase N+1**。远程控制的组包已在 ViewModel，但正式工人页尚未提供安全控制面板（现主要在 DEBUG）。

## 使用与下载

1. 到 Actions 对应 run 的 Artifacts 下载 `app-debug.apk`（不要装 androidTest APK）。
2. 先到系统连接扳手 Wi‑Fi；App 自动 TCP（默认 `192.168.4.1:7888`，正式界面不填端口）；填板号/测点。
3. TCP 连接后收真实过程数据并入库。远程正反转等需按协议先启用远程控制（正式 UI 改造后会明示）。
4. 导出规划升级为带图 Excel；现行为全量 CSV。界面窗口不截断数据库全量。
5. **软件结束采集 ≠ 机械停止。** 旧应用勿直接卸载导致丢数据。

## 源码与协议

- `WifiWrenchAndroid/`：Android 工程目录（包名 `com.chuankangkk.wrenchlift`）。根目录 `App3Android` 为兼容旧 CI 的符号链接；Secrets/keyAlias 残留见接手说明；入口 MainActivity → WrenchLiftApp → WorkerWorkbench（DEBUG 可进 BigScreen 控制台）。
- `最新协议.docx`：厂家 C 协议原文（文内标题仍可能带厂家旧称「App3」，仅作协议文本，**产品对外名是「WiFi 扳手」**）。
- `docs/项目背景.md`：**背景第一页（二次开发从这进）**。
- `docs/设计规范.md`：**UI 交接权威**（单屏分区、连接、视觉、协议映射、验收清单）。
- `docs/design-renders/`：产品方设计稿落位目录（可空）；**非**规范正文；**禁止**塞 AI 软件概念图当定稿。
- `docs/reference-hardware/`：T 形手把真机参照（非 UI 稿）。
- `docs/开发接手说明.md`：模块、禁忌、无硬件测试、**待改名清单**。
- `docs/界面与导出优化方向.md`：锁定决策 + 功能码表 + 阶段。
- `docs/项目背景与开发要求.md`：长约束。
- `AGENTS.md`：Agent 执行规则。
- `.github/workflows/android.yml`、`tools/`：CI 与校验脚本。

仓库只保留软件、授权协议与必要说明；禁止 CAD、真实库、密钥等入库。

仓库地址：https://github.com/1837620622/wifi-wrench-android

## 构建规则

Java 21 + 工程内 Gradle wrapper + Android SDK（CI 安装）。可选本地：

```shell
cd WifiWrenchAndroid
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest assembleRelease
```

版本基线 0.2.0；CI `versionCode=1000+run_number`。调试签名由 Secrets 提供。推送/发布需用户明确授权。
