# WiFi 扭矩扳手顶升采集 Android 版

这是一个 Kotlin Android 第一版工程，用于连接 App3 WiFi 扭矩扳手，通过 TCP 接收 C 协议二进制报文，实时解析扭矩、角度、运行结果、电池电量，并把采样点保存到本机 Room 数据库。

## 当前能力

- Jetpack Compose 横屏实时大屏。
- `java.net.Socket` TCP 客户端，默认设备地址 `192.168.4.1:7888`，失败后尝试默认网关和候选协议端口。
- TCP 连接后自动发送连接检查和设备编号查询。
- 网络诊断显示当前 Wi-Fi、本机 IP、默认网关、扳手网段、IP 可达和端口状态。
- 默认地址连接失败后，自动尝试当前默认网关作为设备地址。
- 正式 APK 不保留 Fake 模拟数据源，界面数据必须来自真实扳手或历史记录。
- C 协议帧解析：`C5 C5` 帧头、长度切帧、累加和校验、粘包拆包处理。
- 功能码：`0x04` 电量、`0x05` 状态、`0x06` 应答、`0x12` 扭矩转角、`0x15` 最终结果、`0x17` 结果确认、`0x21/0x22` 对时、`0x23` 螺母计数、`0x25/0x26` 设备编号、`0x33` 连接保活、`0x44` 位置、`0x55` 特殊模式结果。
- Room 保存测量会话、扭矩角度点、计算力值、平衡点标记和原始 HEX。

## 工程口径

正式顶升力、支反力和平衡点判断必须接入实测标定曲线、丝杆传动参数或结构力学模型，不能使用占位线性系数作为工程验收依据。

## Mac 开发步骤

1. Android 项目真实目录：`/Users/chuankangkk/Downloads/隔振器顶升开发软件/App3Android`。
2. Android Studio 直接打开这个目录，不要打开 `/Users/chuankangkk/Downloads/隔振器顶升开发软件` 父目录。
3. 业务代码保存在项目目录，Android SDK、Gradle 缓存、Kotlin 缓存、构建产物和 Android Studio 缓存放在 SSD：`/Volumes/256G/AndroidDev`。
4. 项目根目录 `local.properties` 固定为 `sdk.dir=/Volumes/256G/AndroidDev`。
5. 新终端会自动使用 SSD 缓存：
   - `ANDROID_USER_HOME=/Volumes/256G/AndroidDev/android-user-home`
   - `ANDROID_AVD_HOME=/Volumes/256G/AndroidDev/android-user-home/avd`
   - `GRADLE_USER_HOME=/Volumes/256G/AndroidDev/gradle-user-home`
   - `APP3_ANDROID_BUILD_ROOT=/Volumes/256G/AndroidDev/app3-android-build`
6. 执行单元测试：

```bash
cd /Users/chuankangkk/Downloads/隔振器顶升开发软件/App3Android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew testDebugUnitTest --offline --no-daemon --console=plain
```

7. 构建调试包：

```bash
cd /Users/chuankangkk/Downloads/隔振器顶升开发软件/App3Android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug --offline --no-daemon --console=plain
```

8. 连接安卓手机并开启 USB 调试后安装：

```bash
cd /Users/chuankangkk/Downloads/隔振器顶升开发软件/App3Android
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:installDebug --no-daemon --console=plain
```

## 目录与占用规则

- 每个产品单独建一个英文 Android 工程目录，业务代码只放在该产品目录内。
- `/Volumes/256G/AndroidDev` 只放公共 Android 环境：SDK、Gradle 缓存、AVD、Android Studio 缓存和构建输出。
- 不要把业务代码放进 `/Volumes/256G/AndroidDev`，也不要每个产品复制一套 SDK。
- 多个 `build-tools` 版本是 Android SDK 的兼容机制，不是多份项目代码；当前项目使用 `compileSdk 36.1`。
- 为降低内存占用，现场调试优先使用真实安卓手机；模拟器只作为没有手机时的备选。
- 若使用模拟器，只保留一个常用 AVD，位置固定在 `/Volumes/256G/AndroidDev/android-user-home/avd`。

## Windows 开发步骤

1. 安装 Android Studio 或 Android SDK Command-line Tools。
2. 在项目根目录创建 `local.properties`，使用 Windows 路径，例如：

```properties
sdk.dir=C\:\\Users\\用户名\\AppData\\Local\\Android\\Sdk
```

3. 运行测试：

```bat
gradlew.bat test
```

4. 构建调试包：

```bat
gradlew.bat assembleDebug
```

## 现场连接建议

- 手机连接扳手 WiFi 后，使用默认 `192.168.4.1:7888` 连接；失败时 App 会尝试默认网关和候选协议端口。
- 连接后 App 会先做一次连接检查并查询设备编号。
- 设备页可查看当前 Wi-Fi、本机 IP、默认网关和端口检查结果。
- 若设备端口不可用，先确认手机仍在扳手 WiFi 网段，再用厂家工具或现场抓包确认实际端口。
- 收到最终结果后，App 会立即自动确认；不要在没有结果时手动发送确认。
- 收到扳手请求校时时，App 会自动回复时间；不要在没有请求时手动对时。
- TCP 连接建立后会周期发送连接保活，避免扳手自动关机。

## 主要目录

- `app/src/main/java/com/chuankangkk/wrenchlift/protocol`：协议解析与组包。
- `app/src/main/java/com/chuankangkk/wrenchlift/connection`：TCP 连接、网络诊断和端点发现。
- `app/src/main/java/com/chuankangkk/wrenchlift/measurement`：实时状态、力值计算和 ViewModel。
- `app/src/main/java/com/chuankangkk/wrenchlift/data`：Room 数据库。
- `app/src/main/java/com/chuankangkk/wrenchlift/ui`：Compose 横屏界面。
- `app/src/test/java/com/chuankangkk/wrenchlift/protocol`：协议单元测试。

## 配套文档

- `../AGENTS.md`：本项目专属记忆，协议、公式、UI 和联调边界写在这里。
- `docs/Android开发环境与缓存规则.md`：Android Studio、SDK、Gradle 和 SSD 缓存规则。
- `docs/功能需求与实现清单.md`：需求完成状态。
- `docs/厂家协议与通讯链路.md`：App3 厂家协议、TCP 链路和安全边界。
- `docs/Kali双网卡协议调试方案.md`：Kali 外置网卡连接扳手、Parallels 共享网络保持联网的调试方法。
- `docs/工程公式与标定边界.md`：位移、力、压力公式和未标定边界。
- `docs/UI与交互设计说明.md`：现场工人横屏界面设计。
- `docs/数据存储与导出设计.md`：Room 数据库和导出计划。
- `docs/现场联调计划.md`：真机和扳手联调步骤。
- `docs/当前调试记录.md`：当前状态、缓存验证、协议实测和待办。
- `../调试报告/App3_全协议接收联调报告.md`：真实设备联调结果和全功能码矩阵。
