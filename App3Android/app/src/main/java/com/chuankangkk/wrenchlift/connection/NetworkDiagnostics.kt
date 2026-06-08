package com.chuankangkk.wrenchlift.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class NetworkDiagnosticResult(
    val wifiName: String,
    val localIp: String,
    val gatewayIp: String,
    val inWrenchSubnet: Boolean,
    val deviceIpReachable: Boolean?,
    val devicePortReachable: Boolean?,
    val gatewayPortReachable: Boolean?,
    val targetHost: String,
    val targetPort: Int,
    val suggestedHost: String,
    val suggestedPort: Int,
    val discoveredEndpoint: String,
    val summary: String,
)

class NetworkDiagnostics(
    context: Context,
    private val timeoutMillis: Int = 900,
    private val subnetScanTimeoutMillis: Int = 260,
) {
    private val appContext = context.applicationContext

    suspend fun diagnose(
        targetHost: String,
        targetPort: Int,
        checkPorts: Boolean,
    ): NetworkDiagnosticResult = withContext(Dispatchers.IO) {
        val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
        val wifiManager = appContext.getSystemService(WifiManager::class.java)
        val network = connectivityManager.activeNetwork
        val capabilities = network?.let(connectivityManager::getNetworkCapabilities)
        val linkProperties = network?.let(connectivityManager::getLinkProperties)

        val wifiName = currentWifiName(capabilities, wifiManager)
        val localIp = linkProperties
            ?.linkAddresses
            ?.mapNotNull { it.address as? Inet4Address }
            ?.firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
            ?: "--"
        val gatewayIp = linkProperties
            ?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway
            ?.hostAddress
            ?: dhcpGateway(wifiManager)
            ?: "--"
        val inWrenchSubnet = isWrenchSubnet(localIp)
        val hostCandidates = candidateHosts(
            localIp = localIp,
            gatewayIp = gatewayIp,
            targetHost = targetHost,
        )
        val portCandidates = candidateProtocolPorts(targetPort)
        val deviceIpReachable = if (checkPorts) reachableIp(targetHost) else null
        val devicePortReachable = if (checkPorts) reachablePort(targetHost, targetPort) else null
        val gatewayPortReachable = if (
            checkPorts &&
            gatewayIp != "--" &&
            gatewayIp != targetHost
        ) {
            reachablePort(gatewayIp, targetPort)
        } else {
            null
        }
        val discovered = if (checkPorts) {
            discoverFirstOpenEndpoint(hostCandidates, portCandidates)
                ?: discoverSubnetEndpoint(localIp, portCandidates)
        } else {
            null
        }
        val suggestedHost = discovered?.first
            ?: if (devicePortReachable == false && gatewayPortReachable == true) gatewayIp else targetHost
        val suggestedPort = discovered?.second ?: targetPort

        NetworkDiagnosticResult(
            wifiName = wifiName,
            localIp = localIp,
            gatewayIp = gatewayIp,
            inWrenchSubnet = inWrenchSubnet,
            deviceIpReachable = deviceIpReachable,
            devicePortReachable = devicePortReachable,
            gatewayPortReachable = gatewayPortReachable,
            targetHost = targetHost,
            targetPort = targetPort,
            suggestedHost = suggestedHost,
            suggestedPort = suggestedPort,
            discoveredEndpoint = discovered?.let { "${it.first}:${it.second}" } ?: "--",
            summary = buildSummary(
                wifiName = wifiName,
                localIp = localIp,
                gatewayIp = gatewayIp,
                inWrenchSubnet = inWrenchSubnet,
                devicePortReachable = devicePortReachable,
                gatewayPortReachable = gatewayPortReachable,
                discoveredEndpoint = discovered?.let { "${it.first}:${it.second}" },
                checkPorts = checkPorts,
            ),
        )
    }

    private fun currentWifiName(
        capabilities: NetworkCapabilities?,
        wifiManager: WifiManager?,
    ): String {
        val ssidFromCapabilities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (capabilities?.transportInfo as? WifiInfo)?.ssid
        } else {
            null
        }
        @Suppress("DEPRECATION")
        val ssidFromWifiManager = wifiManager?.connectionInfo?.ssid
        return cleanSsid(ssidFromCapabilities ?: ssidFromWifiManager)
    }

    private fun reachableIp(host: String): Boolean = runCatching {
        InetAddress.getByName(host).isReachable(timeoutMillis)
    }.getOrDefault(false)

    private fun reachablePort(host: String, port: Int, timeout: Int = timeoutMillis): Boolean = runCatching {
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), timeout)
        }
        true
    }.getOrDefault(false)

    private fun discoverFirstOpenEndpoint(
        hosts: List<String>,
        ports: List<Int>,
    ): Pair<String, Int>? {
        hosts.forEach { host ->
            ports.forEach { port ->
                if (reachablePort(host, port)) return host to port
            }
        }
        return null
    }

    private suspend fun discoverSubnetEndpoint(
        localIp: String,
        ports: List<Int>,
    ): Pair<String, Int>? {
        val prefix = subnetPrefix(localIp) ?: return null
        val semaphore = Semaphore(24)
        return coroutineScope {
            (1..254)
                .map { "$prefix.$it" }
                .filter { it != localIp }
                .map { host ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            ports.firstOrNull { port ->
                                reachablePort(host, port, subnetScanTimeoutMillis)
                            }?.let { port -> host to port }
                        }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .minByOrNull { endpoint -> endpoint.first.substringAfterLast('.').toIntOrNull() ?: 255 }
        }
    }

    @Suppress("DEPRECATION")
    private fun dhcpGateway(wifiManager: WifiManager?): String? {
        val gateway = wifiManager?.dhcpInfo?.gateway ?: return null
        if (gateway == 0) return null
        val bytes = byteArrayOf(
            (gateway and 0xFF).toByte(),
            ((gateway ushr 8) and 0xFF).toByte(),
            ((gateway ushr 16) and 0xFF).toByte(),
            ((gateway ushr 24) and 0xFF).toByte(),
        )
        return runCatching { InetAddress.getByAddress(bytes).hostAddress }.getOrNull()
    }

    companion object {
        fun cleanSsid(raw: String?): String {
            val value = raw
                ?.trim()
                ?.trim('"')
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            return value ?: "--"
        }

        fun isWrenchSubnet(ip: String): Boolean =
            ip.startsWith("192.168.4.") && ip != "192.168.4.1"

        fun candidateHosts(
            localIp: String,
            gatewayIp: String,
            targetHost: String,
        ): List<String> = buildList {
            add(targetHost)
            if (gatewayIp != "--") add(gatewayIp)
            if (localIp.startsWith("192.168.4.")) add("192.168.4.1")
        }
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "--" }
            .distinct()

        fun candidateProtocolPorts(targetPort: Int): List<Int> =
            listOf(targetPort, 7888, 8899, 9000, 10001)
                .filter { it in 1..65535 }
                .distinct()

        fun subnetPrefix(localIp: String): String? {
            if (!localIp.startsWith("192.168.4.")) return null
            val prefix = localIp.substringBeforeLast('.', "")
            return prefix.takeIf { it == "192.168.4" }
        }

        private fun buildSummary(
            wifiName: String,
            localIp: String,
            gatewayIp: String,
            inWrenchSubnet: Boolean,
            devicePortReachable: Boolean?,
            gatewayPortReachable: Boolean?,
            discoveredEndpoint: String?,
            checkPorts: Boolean,
        ): String = when {
            wifiName == "--" -> "未读取到 Wi-Fi 名称，请检查权限或手动确认已连接扳手 Wi-Fi"
            localIp == "--" -> "未获取本机 IP，请重新连接 Wi-Fi"
            !inWrenchSubnet -> "当前不在 192.168.4.x 网段"
            gatewayIp == "--" -> "未获取默认网关"
            !checkPorts -> "网段正常，待连接设备端口"
            devicePortReachable == true -> "默认设备端口可连接"
            gatewayPortReachable == true -> "默认地址失败，网关端口可连接"
            discoveredEndpoint != null -> "已发现可用协议端口 $discoveredEndpoint"
            else -> "设备端口未连通，请确认扳手未休眠"
        }
    }
}
