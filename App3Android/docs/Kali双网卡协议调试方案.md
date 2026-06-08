# Kali 双网卡协议调试方案

本方案用于 App3 扳手协议联调。目标是让 Mac/Codex 继续联网，同时让 Kali 通过外置网卡连接扳手 Wi-Fi。

## 推荐拓扑

```text
Mac / Codex
  -> Parallels Shared Network
  -> Kali eth0：互联网、SSH、prlctl 控制链

Kali wlan0：外置 USB 网卡
  -> 500banshou 扳手 Wi-Fi
  -> 192.168.4.0/24 设备局域网
  -> 192.168.4.1:7888 协议端口
```

关键原则：

- `eth0` 必须保留默认路由，用来联网和给 Codex 控制 Kali。
- `wlan0` 只访问扳手网段，不能抢默认路由。
- 如果连接扳手 Wi-Fi 后 Kali “没网”，优先检查默认路由是否被 `wlan0` 抢走。
- 协议调试以 TCP 实际收发帧为准，不能只看 Wi-Fi 已连接或 ping 是否成功。

## 只读检查命令

```bash
ip -brief addr
ip route
iw dev wlan0 link
nmcli device status
nmcli connection show
```

期望状态：

```text
eth0   UP  10.211.55.x/24
wlan0  UP  192.168.4.x/24
default via 10.211.55.1 dev eth0
192.168.4.0/24 dev wlan0
```

## 连接扳手 Wi-Fi 的安全方式

连接前先确保 `500banshou` 不抢默认路由：

```bash
sudo nmcli connection modify 500banshou ipv4.never-default yes ipv6.never-default yes
```

再连接外置网卡，必须加超时，避免 NetworkManager 或 USB 网卡驱动卡住：

```bash
timeout 20s sudo nmcli connection up 500banshou ifname wlan0
```

如果 `wlan0` 显示 `unmanaged`，不要反复点图形界面；先检查原因，再一次性执行：

```bash
sudo nmcli device set wlan0 managed yes
timeout 20s sudo nmcli connection up 500banshou ifname wlan0
```

如果命令超时，先停止连接流程，不要连续重试：

```bash
sudo pkill -f "nmcli connection up 500banshou" || true
sudo systemctl restart NetworkManager
```

## IP 和端口验证

扳手 Wi-Fi 下常见分配：

- Kali 外置网卡：`192.168.4.x`
- 扳手设备：通常是 `192.168.4.1`
- 协议端口：厂家默认 `7888`
- 配置网页：常见 `80`，只作为 Wi-Fi 模块配置页，不当作协议端口

验证顺序：

```bash
arping -c 2 -I wlan0 192.168.4.1
python3 - <<'PY'
import socket
for port in (7888, 8899, 9000, 10001, 80):
    sock = socket.socket()
    sock.settimeout(2)
    try:
        sock.connect(("192.168.4.1", port))
        print(f"{port}=open")
    except Exception as exc:
        print(f"{port}=closed:{type(exc).__name__}")
    finally:
        sock.close()
PY
```

判断：

- `7888=open` 才说明默认协议端口可连接。
- `80=open` 只说明 Wi-Fi 模块配置页可访问，不代表 App3 协议可用。
- ping 失败不等于协议失败，部分 Wi-Fi 模块会禁 ICMP；TCP 连接和协议帧更可靠。

## 协议联调顺序

先跑安全协议，不自动发送会导致设备动作或写入参数的帧：

1. `0x33` 心跳。
2. `0x25` 查询 SN，等待 `0x26`。
3. 等待 `0x04` 电量。
4. 被动等待 `0x12` 扭矩角度过程数据。
5. 被动等待 `0x15` 最终结果，收到后自动回 `0x17`。
6. 被动等待 `0x21` 请求校时，收到后自动回 `0x22`。
7. 被动等待 `0x55` 特殊模式结果。

不得自动发送：

- `0x01` 正转/反转。
- `0x10` 参数设置。
- `0x50`、`0x51` 特殊模式参数设置。

这些帧会改变设备动作或设备参数，必须在现场安全确认后由人触发。

## Kali 卡住时的处理

如果 Kali 图形界面卡住：

1. 先不要继续下发外置网卡连接命令。
2. 在 Mac 侧停止挂起的 `prlctl exec` 进程。
3. 如果 guest 仍响应，执行：

```bash
sudo pkill -f "nmcli connection up 500banshou" || true
sudo systemctl restart NetworkManager
```

4. 如果 guest 完全不响应，只能在 Parallels 里正常重启 Kali。
5. 重启后先验证 `eth0` 联网，再连接 `wlan0` 扳手 Wi-Fi。

不要把 Kali 的互联网依赖到扳手 Wi-Fi 上。扳手 Wi-Fi 没有公网，正式 App 也按离线方案设计。
