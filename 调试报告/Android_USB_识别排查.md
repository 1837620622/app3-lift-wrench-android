# Android USB 识别排查

## 当前结论

Mac 侧 Android SDK 和 adb 环境正常，但当前没有识别到安卓手机的 ADB 设备。

这不是 App3 代码问题，也不是 Android Studio 项目路径问题。当前更像是 USB 物理链路、转接坞、线材或手机 USB 模式没有切到数据调试。

## 已验证内容

- `ANDROID_HOME=/Volumes/256G/AndroidDev`
- `ANDROID_SDK_ROOT=/Volumes/256G/AndroidDev`
- `adb` 路径：`/Volumes/256G/AndroidDev/platform-tools/adb`
- `zsh -lc` 和 `zsh -lic` 均可识别 `adb`
- adb 版本：`37.0.0-14910828`
- `adb devices -l` 当前为空
- 重启 adb server 后仍为空

## USB 总线观察

底层 USB 树当前能看到：

- USB Hub / USB2.1 Hub / USB3.2 Hub
- USB BillBoard，序列号 `SN23456789`
- EAGET 设备，序列号 `0000000015BC`
- thinkplus 256GB SSD

未看到 Android、Google、Samsung、Huawei、Honor、Xiaomi、OPPO、vivo、realme、OnePlus 等手机厂商或 ADB/MTP 类设备。

系统出现 `/dev/cu.usbmodemSN234567892`，但它更像 USB Billboard/转接设备暴露的串口，不是 Android ADB 设备。ADB 不会通过这个串口安装 APK。

## 判断

如果手机已经被 ADB 看到但未授权，`adb devices` 通常会显示：

```text
unauthorized
```

如果手机被看到但模式不对，USB 总线通常仍会出现手机厂商或 Android 设备。

当前是 `adb devices` 完全为空，并且 USB 树没有手机厂商设备，所以优先排查：

1. USB 线是否只支持充电，不支持数据。
2. 是否通过转接坞连接导致手机数据通道没有透传。
3. 手机是否只在充电模式，没有选择文件传输或 USB 调试。
4. 手机开发者选项和 USB 调试是否真的打开。
5. Mac 隐私与安全里的 USB 配件连接权限是否阻止了新设备。

## 建议操作顺序

1. 用一根确定支持数据传输的 USB-C 数据线。
2. 尽量直连 Mac，不先经过扩展坞。
3. 手机打开开发者选项。
4. 打开 USB 调试。
5. 插线后手机下拉通知栏，选择 USB 用途为“文件传输”或“MTP”。
6. 手机弹出“允许 USB 调试”时选择允许，最好勾选始终允许。
7. Mac 端运行：

```bash
/Volumes/256G/AndroidDev/platform-tools/adb devices -l
```

8. 如果看到 `unauthorized`，看手机授权弹窗。
9. 如果仍为空，换线、换 Mac 端口、去掉扩展坞后再试。

## 成功标准

成功时应看到类似：

```text
List of devices attached
XXXXXXXX    device usb:... product:... model:... device:...
```

之后才能执行：

```bash
/Volumes/256G/AndroidDev/platform-tools/adb install -r \
  /Volumes/256G/AndroidDev/app3-android-build/app/outputs/apk/debug/app-debug.apk
```
