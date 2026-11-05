package com.chuankangkk.wrenchlift.connection

sealed class ConnectionState(val label: String) {
    data object Disconnected : ConnectionState("未连接")
    data object Connecting : ConnectionState("连接中")
    data class Connected(val host: String, val port: Int) : ConnectionState("已连接")
    data class Error(val message: String) : ConnectionState("断线")

    val isOnline: Boolean
        get() = this is Connected
}
