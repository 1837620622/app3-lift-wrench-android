# App3 UI 模拟器核查报告

## 环境

- 模拟器：`Pixel_10_Pro_XL`
- 分辨率：`2992 x 1344` 横屏
- APK：`/Volumes/256G/AndroidDev/app3-android-build/app/outputs/apk/debug/app-debug.apk`
- 说明：该 AVD 提示推荐 16GB 内存，当前主机可用内存约 8GB，运行过程中出现过 System UI ANR 和 Google Play 服务崩溃。App 本身没有 FATAL 崩溃栈，但该 AVD 不适合作为最终性能验收环境。

## 截图证据

- 首屏旧版截图：`调试报告/ui_screenshots/app3_pixel10_home_clean.png`
- 放大曲线截图：`调试报告/ui_screenshots/app3_pixel10_focused_chart.png`
- 底部留白修复后首屏：`调试报告/ui_screenshots/app3_pixel10_restart_after_padding.png`
- 左侧控制区滚动后：`调试报告/ui_screenshots/app3_pixel10_left_scroll_2.png`

## 已验证

- APK 可以安装。
- App 可以启动并渲染主界面。
- 首页为横屏工程大屏风格，顶部状态、主曲线和关键读数清晰。
- `放大曲线` 按钮有效，可进入大曲线界面。
- 放大曲线页有 `返回工作台`、曲线指标切换、停止扳手、保存记录、确认平衡点。
- 左侧控制区可以滚动，能看到 `手机控制`、`关闭控制`、`正向顶升`、`反向回退`。
- 未连接时高风险按钮为灰色禁用态，符合安全要求。
- crash buffer 中出现的是 `com.google.android.gms` 崩溃，不是 App 进程崩溃。

## 发现的问题

- Pixel10 横屏高度有限，首屏左侧只能看到连接区和一部分操作区；虽然可以滚动，但对现场工人来说“正向顶升/反向回退”不够第一眼可见。
- 放大曲线页整体可读，但底部操作条在短屏横屏下仍偏靠近系统导航条。
- 当前模拟器资源不足导致系统 UI ANR，后续建议用真实安卓手机或更轻量 AVD 做最终 UI 验收。

## 已做修复

- 主界面外层增加底部安全留白。
- 左侧滚动容器增加底部 padding。
- 放大曲线页增加底部 padding。

## 下一步 UI 优化建议

1. 真实手机到手后按实际屏幕比例重新截图。
2. 如果工人现场需要更快操作，把 `正向顶升`、`反向回退`、`停止扳手` 固定到左侧控制区底部或顶部，避免需要滚动。
3. 未标定阶段保留曲线大屏，但正式力值区域继续显示“未标定不输出正式顶升力”，避免工程误用。
