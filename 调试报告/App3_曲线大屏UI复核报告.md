# App3 曲线大屏 UI 复核报告

## 复核结论

- 曲线大屏已改为单主曲线模式：同一时间只显示扭矩、顶升力、浮置板压力中的一张曲线。
- 扭矩、顶升力、压力通过顶部 48dp 切换按钮切换，不再把多张曲线并排或上下堆叠成细线。
- 工作台继续显示关键读数，曲线大屏只负责清晰观察趋势。
- 小高度横屏下仍保留曲线切换按钮，避免进入大屏后无法切换指标。
- 正式 APK 仍未恢复模拟采集入口，曲线数据来自真实 TCP 回传或历史记录。

## 设计依据

- Android 官方建议 Compose 应用根据当前可用宽高自适应布局，不能依赖固定屏幕型号。
- Android 16 起，大屏设备可能忽略应用声明的固定方向和尺寸限制，因此曲线页必须能适配可调整窗口。
- Android 可访问性建议触控目标至少 48dp，本轮保留曲线切换按钮和主要操作按钮的 48dp 以上高度。

参考：

- https://developer.android.com/develop/ui/compose/layouts/adaptive/support-different-screen-sizes
- https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability
- https://support.google.com/accessibility/android/answer/7101858

## 本轮修改点

- `FocusedChartScreen`：
  - 删除右侧/下方副曲线区域。
  - 删除曲线状态卡，避免占用主图空间。
  - 保留顶部指标切换：扭矩、顶升力、压力。
  - 主曲线占满剩余主体区域。
- `ConnectionPanel`：
  - 连接中禁用连接按钮，避免重复点击触发重复 TCP 连接。
- `PointMapScreen`：
  - 点位坐标提升为稳定常量。
  - Canvas 内文字画笔减少重复创建。
- 文档：
  - 删除“同时显示三条曲线”的旧描述。
  - 明确曲线大屏不得多图挤压，必须单主曲线可读。

## 验证命令

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_HOME=/Volumes/256G/AndroidDev \
ANDROID_SDK_ROOT=/Volumes/256G/AndroidDev \
GRADLE_USER_HOME=/Volumes/256G/AndroidDev/gradle-user-home \
./gradlew lintDebug testDebugUnitTest assembleDebug --offline --no-daemon --console=plain \
  -Papp3.android.buildRoot="/Volumes/256G/AndroidDev/app3-android-build"
```

结果：

```text
BUILD SUCCESSFUL in 48s
53 actionable tasks: 18 executed, 35 up-to-date
```

调试 APK：

```text
调试报告/apk/app3-wrench-lift-debug-0.1.0.apk
SHA256: 28c91092345de18075f491ae562df2c6a21707cff59792ee23307d14bfceaf98
```

## Lint 剩余提醒

- `targetSdk` 仍是 35，lint 提示不是最新 Android 版本。
- Gradle、Compose BOM、Lifecycle、Coroutines 有可更新版本。
- `sensorLandscape` 在 Android 16 大屏上可能被系统忽略，所以仍需保持自适应布局。

这些提醒不是本轮曲线大屏改动引入的编译错误。

## 仍需真机复核

- 真实安卓手机横屏安装后，截图确认主曲线高度、坐标轴、采样点和当前值是否清楚。
- 检查正横屏和反横屏，确认手机固定在工具或支架上时可以看清。
- 真机连接扳手后，用真实 `0x12` 过程帧观察曲线是否持续上升并在平衡附近趋稳。
