package com.chuankangkk.wrenchlift.connection

/** 扳手 TCP 端点。只在后台探测与高级诊断里出现，正式工人界面不展示。 */
data class WrenchEndpoint(val host: String, val port: Int) {
    override fun toString(): String = "$host:$port"

    companion object {
        const val DEFAULT_HOST = "192.168.4.1"
        const val DEFAULT_PORT = 7888
        val DEFAULT = WrenchEndpoint(DEFAULT_HOST, DEFAULT_PORT)

        /** 非法或空值返回 null，避免把半截输入写成缓存端点。 */
        fun parse(host: String?, port: String?): WrenchEndpoint? {
            val cleanHost = host?.trim().orEmpty()
            val cleanPort = port?.trim()?.toIntOrNull() ?: return null
            if (cleanHost.isBlank() || cleanHost == "--" || cleanPort !in 1..65535) return null
            return WrenchEndpoint(cleanHost, cleanPort)
        }
    }
}

/**
 * 设计规范 NET-01 / NET-07 的探测顺序：
 * ① 上次握手成功的端点；② 默认 192.168.4.1:7888；③ 高级诊断里保存的端点 + 候选主机 × 候选端口；
 * ④ 扳手子网短扫（由调用方在 ①～③ 全部失败后执行）。
 */
object EndpointPlanner {
    val CANDIDATE_PORTS = listOf(WrenchEndpoint.DEFAULT_PORT, 8899, 9000, 10001)

    /** 不需要读网络信息就能直接尝试的端点：缓存 → 默认。没有缓存时第一个就是默认端点。 */
    fun primary(lastGood: WrenchEndpoint?): List<WrenchEndpoint> =
        listOfNotNull(lastGood, WrenchEndpoint.DEFAULT).distinct()

    /** 候选主机 × 候选端口（已去重，并排除 primary 已试过的端点）。 */
    fun candidates(
        lastGood: WrenchEndpoint?,
        manual: WrenchEndpoint?,
        gatewayIp: String,
        localIp: String,
    ): List<WrenchEndpoint> {
        val hosts = buildList {
            lastGood?.let { add(it.host) }
            add(WrenchEndpoint.DEFAULT_HOST)
            manual?.let { add(it.host) }
            if (gatewayIp.isUsableHost()) add(gatewayIp)
        }.map { it.trim() }.filter { it.isUsableHost() && it != localIp }.distinct()
        val ports = buildList {
            lastGood?.let { add(it.port) }
            add(WrenchEndpoint.DEFAULT_PORT)
            manual?.let { add(it.port) }
            addAll(CANDIDATE_PORTS)
        }.filter { it in 1..65535 }.distinct()
        val alreadyTried = primary(lastGood).toSet()
        val ordered = buildList {
            // 手动保存的完整端点优先于拼出来的组合。
            manual?.let { add(it) }
            hosts.forEach { host -> ports.forEach { port -> add(WrenchEndpoint(host, port)) } }
        }
        return ordered.distinct().filterNot { it in alreadyTried }
    }

    private fun String.isUsableHost(): Boolean = isNotBlank() && this != "--" && this != "0.0.0.0"
}
