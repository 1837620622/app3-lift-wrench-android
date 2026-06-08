# Android 开发环境与缓存规则

## 固定目录

Android 业务代码和 Android 环境分开管理：

```text
业务代码：
/Users/chuankangkk/Downloads/隔振器顶升开发软件/App3Android

Android 公共环境：
/Volumes/256G/AndroidDev
```

Android Studio 必须直接打开 `App3Android`，不要打开父目录 `隔振器顶升开发软件`，否则容易把资料目录、旧项目和建模图一起误识别成工程内容。

## SSD 环境变量

```bash
export ANDROID_HOME=/Volumes/256G/AndroidDev
export ANDROID_SDK_ROOT=/Volumes/256G/AndroidDev
export ANDROID_USER_HOME=/Volumes/256G/AndroidDev/android-user-home
export ANDROID_AVD_HOME=/Volumes/256G/AndroidDev/android-user-home/avd
export GRADLE_USER_HOME=/Volumes/256G/AndroidDev/gradle-user-home
export APP3_ANDROID_BUILD_ROOT=/Volumes/256G/AndroidDev/app3-android-build
```

关键命令路径：

```text
adb      /Volumes/256G/AndroidDev/platform-tools/adb
emulator /Volumes/256G/AndroidDev/emulator/emulator
```

## 当前 SDK 状态

```text
platforms/android-36.1
build-tools/36.1.0
build-tools/37.0.0
platform-tools
emulator
```

当前未发现 `cmdline-tools/latest/bin/sdkmanager`。后续如需安装 SDK 组件，应补到 `/Volumes/256G/AndroidDev/cmdline-tools/latest/`，不要下载到业务项目目录。

## 缓存固定策略

以下路径必须长期保留：

```text
~/.gradle
  -> /Volumes/256G/AndroidDev/gradle-user-home

~/.android
  -> /Volumes/256G/AndroidDev/android-user-home

~/Library/Caches/Google/AndroidStudio2026.1.1
  -> /Volumes/256G/AndroidDev/android-studio-state/Caches/Google/AndroidStudio2026.1.1
```

Android Studio 配置目录中的 `idea.properties` 已显式设置：

```properties
idea.system.path=/Volumes/256G/AndroidDev/android-studio-state/Caches/Google/AndroidStudio2026.1.1
idea.log.path=/Volumes/256G/AndroidDev/android-studio-state/Logs/Google/AndroidStudio2026.1.1
```

项目级构建输出和缓存：

```text
/Volumes/256G/AndroidDev/app3-android-build
├── app/
├── project-cache/
└── kotlin-project-data/
```

## 不要清理的内容

- Android SDK。
- Gradle wrapper 和 Gradle module cache。
- Kotlin 持久缓存。
- Android Studio index cache。
- AVD 模拟器目录。
- 当前项目的 `project-cache`。
- 正在复用的 `app3-android-build`。

这些缓存被删除后，Android Studio 会重新导入、重新索引、重新下载依赖，表现为打开项目后长时间卡顿。

## 可以清理的内容

- 确认不需要的旧 APK。
- 明确废弃的临时目录。
- 失败构建留下的无用临时文件。
- 确认不再使用的旧 SDK 版本。

清理前先确认没有 Android Studio、Gradle daemon、模拟器正在运行。

## 验证命令

```bash
zsh -lc 'echo $ANDROID_HOME; echo $GRADLE_USER_HOME; adb version'
zsh -lic 'echo $ANDROID_HOME; echo $GRADLE_USER_HOME; adb version'
realpath ~/.gradle
realpath ~/.android
realpath ~/Library/Caches/Google/AndroidStudio2026.1.1
```

离线验证 Gradle 依赖缓存：

```bash
cd /Users/chuankangkk/Downloads/隔振器顶升开发软件/App3Android
env -u GRADLE_USER_HOME -u ANDROID_HOME -u ANDROID_SDK_ROOT ./gradlew help --offline --no-daemon --console=plain
```

该命令能成功说明 Android Studio 图形界面即使没有继承终端环境变量，也能通过软链接复用 SSD 缓存。
