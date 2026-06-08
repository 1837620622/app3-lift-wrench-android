# 隔振器顶升开发软件项目总览

本目录用于保存 App3 WiFi 扭矩扳手顶升采集软件的资料、源码和调试证据。当前正式开发方向是 Android 离线采集 App，旧桌面版 PyQt 项目保留为作废参考，不再作为主线继续开发。

## 目录定位

```text
隔振器顶升开发软件/
├── App3Android/                 当前 Android 工程，Android Studio 直接打开此目录
├── 建模图/                       结构、测力、顶升方案资料
├── 最新协议.docx                 厂家 App3 通讯协议说明书
└── 顶升扳手采集软件（作废）/       旧 PyQt 桌面项目，仅作参考
```

## 当前主线

- 开发设备：Mac Apple Silicon / M1。
- 开发工具：Android Studio。
- Android SDK 与构建缓存：`/Volumes/256G/AndroidDev`。
- 业务代码目录：`/Users/chuankangkk/Downloads/隔振器顶升开发软件/App3Android`。
- Android Studio 必须直接打开 `App3Android`，不要打开父目录。
- 现场使用方式：安卓手机连接扳手 Wi-Fi，App 离线完成 TCP 连接、协议解析、工程计算、曲线显示和本机数据保存。

## 核心目标

- 扫描或引导连接扳手 Wi-Fi。
- 默认连接 `192.168.4.1:7888`，失败后尝试默认网关和候选协议端口，并允许手动修改 IP 和端口。
- 通过 TCP 接收厂家协议二进制帧。
- 解析扭矩、角度、运行结果、电量、SN、心跳等数据。
- 根据角度、扭矩和标定参数计算顶升位移、顶升力和浮置板压力。
- 横屏显示实时大屏曲线，适配不同安卓手机尺寸。
- 保存原始 HEX 和工程数据，支持历史查询、回放和导出。
- 正式 Android APK 不保留模拟模式，采集数据必须来自真实扳手 TCP 回传。

## 文档入口

- [项目级 AGENTS 记忆](AGENTS.md)
- [Android 开发环境与缓存规则](App3Android/docs/Android开发环境与缓存规则.md)
- [功能需求与实现清单](App3Android/docs/功能需求与实现清单.md)
- [厂家协议与通讯链路](App3Android/docs/厂家协议与通讯链路.md)
- [Kali 双网卡协议调试方案](App3Android/docs/Kali双网卡协议调试方案.md)
- [工程公式与标定边界](App3Android/docs/工程公式与标定边界.md)
- [UI 与交互设计说明](App3Android/docs/UI与交互设计说明.md)
- [数据存储与导出设计](App3Android/docs/数据存储与导出设计.md)
- [现场联调计划](App3Android/docs/现场联调计划.md)
- [当前调试记录](App3Android/docs/当前调试记录.md)
- [App3 全协议接收联调报告](调试报告/App3_全协议接收联调报告.md)

## 重要边界

- 不在正式界面保留模拟按钮，避免现场误触和数据混淆。
- 未完成标定前，不把顶升力和压力作为正式工程验收值。
- 未确认现场安全前，不自动发送正转、反转或参数写入指令。
- 不把 Android SDK、Gradle 缓存、AVD 或 Android Studio 缓存放进业务项目目录。
- 不日常清理可复用缓存，避免 Android Studio 每次重新导入和重新索引。
