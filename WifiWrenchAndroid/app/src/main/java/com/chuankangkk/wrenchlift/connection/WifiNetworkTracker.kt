package com.chuankangkk.wrenchlift.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Socket

/**
 * 跟踪系统 Wi‑Fi，并让 TCP socket 绑定到 Wi‑Fi 网络。
 *
 * 扳手热点通常不能上网：系统可能仍把移动数据当默认网络，未绑定的 socket 会走蜂窝而连不上
 * 192.168.4.x。绑定只影响本 App 的扳手通信 socket，不改变协议内容。
 */
class WifiNetworkTracker(context: Context) : AutoCloseable {
    private val connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    @Volatile
    private var wifiNetwork: Network? = null

    private val _wifiAvailable = MutableStateFlow(false)
    val wifiAvailable: StateFlow<Boolean> = _wifiAvailable.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            wifiNetwork = network
            _wifiAvailable.value = true
        }

        override fun onLost(network: Network) {
            if (wifiNetwork == network) {
                wifiNetwork = null
                _wifiAvailable.value = false
            }
        }
    }

    private val registered: Boolean = runCatching {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager?.registerNetworkCallback(request, callback)
        connectivityManager != null
    }.getOrDefault(false)

    /** 新建 socket；有 Wi‑Fi 网络时绑定到 Wi‑Fi，绑定失败则退回系统默认路由。 */
    fun newSocket(): Socket {
        val socket = Socket()
        wifiNetwork?.let { network -> runCatching { network.bindSocket(socket) } }
        return socket
    }

    override fun close() {
        if (registered) runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
    }
}
