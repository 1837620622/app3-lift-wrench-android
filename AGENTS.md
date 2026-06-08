# App3 扳手顶升项目记忆

本文件只适用于 `/Users/chuankangkk/Downloads/隔振器顶升开发软件` 项目。全局环境和通用开发习惯写在 `/Users/chuankangkk/.codex/AGENTS.md`，不要把本项目协议、公式、UI 方案和调试记录写回全局文件。

## 1. 项目目录

- 业务父目录：`/Users/chuankangkk/Downloads/隔振器顶升开发软件`。
- 当前 Android 项目目录：`/Users/chuankangkk/Downloads/隔振器顶升开发软件/App3Android`。
- 最新协议文件：`/Users/chuankangkk/Downloads/隔振器顶升开发软件/最新协议.docx`。
- 建模与力学资料目录：`/Users/chuankangkk/Downloads/隔振器顶升开发软件/建模图`。
- 旧桌面项目目录：`/Users/chuankangkk/Downloads/隔振器顶升开发软件/顶升扳手采集软件（作废）`，仅作参考，不作为主线继续开发。
- Android Studio 必须直接打开 `App3Android`，不要打开父目录。

## 2. 当前主线

- 当前方向是 Android 离线方案。
- Mac M1 开发，真实安卓手机优先调试。
- 手机连接扳手 Wi-Fi 后可能没有互联网，App 必须离线完成连接、采集、解析、计算、存储、查询和回放。
- 正式 APK 不保留模拟采集入口和 Fake 数据源；界面数据必须来自真实扳手 TCP 回传或历史记录。

## 3. 本项目 Android 缓存

- 本项目业务代码只放在 `App3Android`。
- Android 公共环境使用 `/Volumes/256G/AndroidDev`。
- 本项目构建根目录：`/Volumes/256G/AndroidDev/app3-android-build`。
- 本项目 `gradle.properties` 中应保留：
  - `app3.android.buildRoot=/Volumes/256G/AndroidDev/app3-android-build`
  - `org.gradle.projectcachedir=/Volumes/256G/AndroidDev/app3-android-build/project-cache`
  - `kotlin.project.persistent.dir=/Volumes/256G/AndroidDev/app3-android-build/kotlin-project-data`
- 清理本项目缓存时，不要删除正在复用的 `project-cache` 和 `kotlin-project-data`。

## 4. 通讯链路

正式软件链路：

```text
安卓手机
  -> 连接扳手 Wi-Fi
  -> 扳手设备 192.168.4.1:7888
```

调试辅助链路：

```text
Mac 开发端
  -> SSH 到 Kali
  -> Kali 连接扳手 Wi-Fi
  -> 端口转发或抓包
```

Kali 只用于调试、抓包和保持 Mac 联网，不进入正式 App 架构。

## 5. 连接策略

- 扳手默认 IP 和端口：`192.168.4.1:7888`。
- 端口默认使用厂家协议值，但界面必须允许手动输入 IP 和端口。
- 若默认 IP/端口失败，优先尝试当前默认网关，再在 `192.168.4.x` 网段扫描候选协议端口 `7888/8899/9000/10001`，最后允许手动输入。
- 连接成功后通过 TCP 接收二进制字节流。
- 必须保存原始 HEX，再保存解析后的工程数据。
- 需要心跳保活，避免扳手空闲休眠或 TCP 被设备主动关闭。
- Android TCP 连接后应自动发送一次连接检查和查询 SN，设备编号成功回传后显示在界面。
- Wi-Fi 已显示连接不代表 `192.168.4.1` 立即可通信；若邻居表显示 `FAILED`，ping/TCP 可能误判失败，需要触发 ARP 或重新连接。
- Android 设备页已实现网络诊断：当前 Wi-Fi、本机 IP、默认网关、`192.168.4.x` 网段、IP 可达、端口可连、自动发现端点。
- 默认 `192.168.4.1:7888` 失败后，Android 会自动尝试默认网关和候选协议端口。
- Kali 调试必须采用双网卡：`eth0` 保持 Parallels 共享网络和默认路由，`wlan0` 外置网卡只连接扳手 Wi-Fi，不允许 `wlan0` 抢默认路由。

## 6. 厂家协议记忆

基础帧格式：

```text
C5 C5 | 地址码 | 功能码 | FF FF | 数据长度 | 数据内容 | 校验码
```

校验码为从帧头到数据内容结束的单字节累加和。

已知功能码：

- `0x04` 电量。
- `0x06` ACK/NAK。
- `0x12` 扭矩角度过程数据。
- `0x15` 执行结果。
- `0x17` 结果确认。
- `0x21` 扳手请求对时。
- `0x22` App 对时。
- `0x25` 查询 SN。
- `0x26` 应答 SN。
- `0x33` 心跳。
- `0x44` GPS。
- `0x55` 脉冲、离合、冲击执行结果。

固定帧：

```text
心跳帧    C5 C5 01 33 FF FF 01 00 BD
查询 SN   C5 C5 01 25 FF FF 05 00 00 00 00 00 B3
结果确认  C5 C5 01 17 FF FF 01 00 A1
```

解析口径：

- `0x12` 和普通 `0x15` 示例中扭矩、角度按 2 字节大端整数解析，暂不做 `/10`。
- `0x15` 结果状态位在第 5 字节，即 `reservedHigh`，不是数据内容第 1 字节；实测结果帧状态可能为 `0xFF`，此时显示“未知状态”但仍同步目标/实际扭矩角度。
- 真实硬件联调发现偏差时，必须回到说明书和抓包复核缩放。
- `0x55` 脉冲类结果扭矩字段按 `0-65535` 对应 `0-6553.5Nm`，解析时 `/10`。
- 电量帧需要兼容说明书中的 `A5 A5` 和现场实测 `C5 C5`。

安全边界：

- 默认不得自动发送正转启动。
- 默认不得自动发送反转启动。
- 默认不得自动发送参数设置 `0x10`。
- 默认不得自动发送脉冲参数设置 `0x50`。
- 收到 `0x15` 结果帧后发送 `0x17` 确认帧。

## 7. 工程公式记忆

位移公式：

```text
displacement_mm = angle_deg / 360 * screw_lead_mm / gear_ratio + zero_offset_mm
```

`gear_ratio` 定义为输入转角与丝杆实际转角的比值。

丝杆物理力模型：

```text
force = 2 * pi * torque_nm * gear_ratio * efficiency / lead_m
```

压力公式：

```text
pressure_mpa = force_n / effective_area_mm2
```

说明：

- `1 N/mm2 = 1 MPa`。
- 未标定时不输出正式顶升力和压力，只显示扳手原始扭矩/角度和“未标定”提示。
- 平衡点、预警力和临界点必须结合现场标定曲线、结构参数和实测数据确定，不能仅凭占位公式作为工程验收依据。

## 8. UI 记忆

- UI 必须面向地铁现场工人，横屏优先，数值醒目，曲线清晰，按钮可触达。
- 大屏要能清楚看到扭矩、顶升力、压力随角度逐步增大并接近平衡点的过程。
- 顶升力是现场主读数，显示优先级高于扭矩、压力和位移；状态条需要同步显示现场判断、最终结果和平衡趋势。
- 支持点击曲线或按钮进入更大的可视化界面。
- 不写“协议几乘几”这类用户看不懂的按钮文案。
- 全中文界面，工程风格，避免 AI 味、白灰块堆叠和低级按钮。
- 不同手机屏幕要自适应，必要区域可上下滑动，文字不得超出边框。
- 不允许在正式界面出现模拟按钮。
- 每个按钮必须有真实行为或明确禁用态，不放无效按钮。
- 正转、反转、参数设置等高风险操作必须有二次确认。

## 9. 当前已知问题

- `BigScreen.kt` 的 Compose 编译错误已修复。
- 当前 Android 单元测试和调试 APK 离线打包已通过。
- 真机尚未连接，仍需安装到真实安卓手机后截图检查横屏 UI、按钮可触达性和现场可读性。
- Android Wi-Fi 扫描、手动连接引导、运行时权限引导、CSV/Excel 导出、历史回放控制、正式标定参数管理和高风险按钮二次确认仍需继续实现。

## 10. 项目文档

- `README.md`：父目录总览。
- `App3Android/README.md`：Android 工程说明。
- `App3Android/docs/Android开发环境与缓存规则.md`：本机 Android 环境和 SSD 缓存。
- `App3Android/docs/功能需求与实现清单.md`：需求完成度。
- `App3Android/docs/厂家协议与通讯链路.md`：协议和链路。
- `App3Android/docs/Kali双网卡协议调试方案.md`：Kali 外置网卡和 Parallels 共享网络并行调试方案。
- `App3Android/docs/工程公式与标定边界.md`：公式和标定边界。
- `App3Android/docs/UI与交互设计说明.md`：界面设计。
- `App3Android/docs/数据存储与导出设计.md`：Room 和导出设计。
- `App3Android/docs/现场联调计划.md`：真机和扳手联调步骤。
- `App3Android/docs/当前调试记录.md`：当前状态和待办。
- `调试报告/App3_全协议接收联调报告.md`：真实设备联调结果和全功能码矩阵。
