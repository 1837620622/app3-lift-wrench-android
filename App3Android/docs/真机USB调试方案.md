# 真机 USB 调试方案

## 结论

当前 Mac 是 Apple M1、8GB 内存。真机 USB 调试可以做，比模拟器轻很多；模拟器会额外占用一整套 Android 系统资源，不适合当前机器长时间联调。

推荐固定方案：

```text
Mac
  -> 保持正常联网，用于 Android Studio、Codex、GitHub、文档和日志分析

安卓手机
  -> USB 连接 Mac，用 adb 安装 APK 和读取日志
  -> 手机 Wi-Fi 连接扳手热点
  -> App 直接访问扳手 TCP
```

这样手机连接无互联网的扳手 Wi-Fi 时，不影响 Mac 继续联网。

## 为什么真机不会像模拟器那么卡

- 模拟器是在 Mac 上运行一台完整 Android 设备，会占 CPU、内存、显存和磁盘 I/O。
- 真机调试只需要 Android Studio 编译 APK，再通过 USB 传到手机。
- 扳手 Wi-Fi、TCP 连接、图表绘制、权限弹窗都在手机上跑，Mac 只负责看日志和改代码。

## Mac 使用建议

- 不启动 Pixel 模拟器。
- Android Studio 只打开 `App3Android`，不要打开父目录。
- 编译时关闭大型 Chrome 标签页、WPS、Telegram、ToDesk 等非必要应用。
- 保持 `GRADLE_USER_HOME=/Volumes/256G/AndroidDev/gradle-user-home`，复用 SSD Gradle 缓存。
- 使用 Android Studio 自带 JBR：

```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## 手机准备

1. 手机开启开发者选项。
2. 打开 USB 调试。
3. 用数据线连接 Mac。
4. 手机弹出 RSA 授权时选择允许。
5. 手机横屏锁定或打开自动旋转，现场优先横屏使用。
6. 手机连接扳手 Wi-Fi。

不建议使用无线调试，因为手机连接扳手 Wi-Fi 后可能没有互联网，也不一定和 Mac 在同一个局域网。

## Mac 侧命令

确认设备：

```bash
/Volumes/256G/AndroidDev/platform-tools/adb devices -l
```

构建调试包：

```bash
cd /Users/chuankangkk/Downloads/隔振器顶升开发软件/App3Android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
GRADLE_USER_HOME="/Volumes/256G/AndroidDev/gradle-user-home" \
./gradlew assembleDebug --offline --no-daemon --console=plain \
  -Papp3.android.buildRoot="/Volumes/256G/AndroidDev/app3-android-build"
```

安装 APK：

```bash
/Volumes/256G/AndroidDev/platform-tools/adb install -r \
  /Volumes/256G/AndroidDev/app3-android-build/app/outputs/apk/debug/app-debug.apk
```

启动 App：

```bash
/Volumes/256G/AndroidDev/platform-tools/adb shell monkey \
  -p com.chuankangkk.wrenchlift 1
```

查看日志：

```bash
/Volumes/256G/AndroidDev/platform-tools/adb logcat \
  | grep -E "wrench|Wrench|Tcp|Socket|App3|AndroidRuntime"
```

保存日志：

```bash
mkdir -p /Users/chuankangkk/Downloads/隔振器顶升开发软件/调试报告/android_logs
/Volumes/256G/AndroidDev/platform-tools/adb logcat -d > \
  /Users/chuankangkk/Downloads/隔振器顶升开发软件/调试报告/android_logs/app3_true_device_logcat.txt
```

截图：

```bash
mkdir -p /Users/chuankangkk/Downloads/隔振器顶升开发软件/调试报告/android_screenshots
/Volumes/256G/AndroidDev/platform-tools/adb exec-out screencap -p > \
  /Users/chuankangkk/Downloads/隔振器顶升开发软件/调试报告/android_screenshots/app3_true_device.png
```

## Android Studio 操作方式

1. 打开 `/Users/chuankangkk/Downloads/隔振器顶升开发软件/App3Android`。
2. 等 Gradle Sync 完成。
3. 顶部设备选择真实手机，不选择 Pixel 模拟器。
4. 点击 Run 安装到手机。
5. 用 Logcat 面板过滤：

```text
package:com.chuankangkk.wrenchlift
```

## 现场联调流程

1. 手机 USB 连接 Mac，确认 `adb devices -l` 能看到设备。
2. 手机连接扳手 Wi-Fi。
3. App 打开后进入现场工作台。
4. 先看设备页：当前 Wi-Fi、本机 IP、默认网关、网段、端口。
5. 点击连接扳手。
6. 连接成功后观察设备编号、电量、原始 HEX。
7. 扳手实际动作后进入曲线大屏，看扭矩、顶升力、压力曲线。
8. 如果没有曲线，优先查日志和原始 HEX，不先改公式。

## 当前机器判断

- 机器配置：MacBook Pro M1，8GB 内存。
- SSD `/Volumes/256G` 当前空间足够。
- 当前没有 emulator/qemu/Gradle daemon 在后台运行。
- 真机调试可行，但不要同时开模拟器、大量浏览器标签和多个大型桌面软件。
