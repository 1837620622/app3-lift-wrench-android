package com.chuankangkk.wrenchlift.connection

import com.chuankangkk.wrenchlift.protocol.WrenchPacket
import com.chuankangkk.wrenchlift.protocol.WrenchPacketBuilder
import com.chuankangkk.wrenchlift.protocol.WrenchPacketParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class TcpWrenchClient(
    private val connectTimeoutMillis: Int = 5000,
    private val heartbeatIntervalMillis: Long = 60_000L,
) : WrenchConnection {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val parser = WrenchPacketParser()
    private val sendMutex = Mutex()
    private var socket: Socket? = null
    private var readJob: Job? = null
    private var heartbeatJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _incomingPackets = MutableSharedFlow<WrenchPacket>(extraBufferCapacity = 64)
    override val incomingPackets: SharedFlow<WrenchPacket> = _incomingPackets

    override suspend fun connect(host: String, port: Int) {
        disconnect()
        _connectionState.value = ConnectionState.Connecting
        withContext(Dispatchers.IO) {
            runCatching {
                Socket().also { newSocket ->
                    newSocket.tcpNoDelay = true
                    newSocket.keepAlive = true
                    newSocket.connect(InetSocketAddress(host, port), connectTimeoutMillis)
                    socket = newSocket
                }
            }.onSuccess {
                _connectionState.value = ConnectionState.Connected(host, port)
                startReadLoop()
                startHeartbeatLoop()
            }.onFailure { throwable ->
                closeSocketQuietly()
                _connectionState.value = ConnectionState.Error(throwable.message ?: "TCP 连接失败")
            }
        }
    }

    override suspend fun disconnect() {
        heartbeatJob?.cancelAndJoin()
        heartbeatJob = null
        readJob?.cancelAndJoin()
        readJob = null
        closeSocketQuietly()
        parser.reset()
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun send(packet: WrenchPacket) {
        val activeSocket = socket ?: error("TCP 未连接")
        sendMutex.withLock {
            withContext(Dispatchers.IO) {
                activeSocket.getOutputStream().apply {
                    write(packet.toFrame())
                    flush()
                }
            }
        }
    }

    private fun startReadLoop() {
        readJob = scope.launch {
            val readBuffer = ByteArray(1024)
            try {
                while (true) {
                    val currentSocket = socket ?: break
                    val count = currentSocket.getInputStream().read(readBuffer)
                    if (count < 0) break
                    val packets = parser.append(readBuffer.copyOfRange(0, count))
                    packets.forEach { _incomingPackets.emit(it) }
                }
                if (socket != null) {
                    _connectionState.value = ConnectionState.Error("TCP 连接已关闭")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (throwable: Throwable) {
                if (socket != null) {
                    _connectionState.value = ConnectionState.Error(throwable.message ?: "TCP 读取异常")
                }
            } finally {
                closeSocketQuietly()
            }
        }
    }

    private fun startHeartbeatLoop() {
        heartbeatJob = scope.launch {
            while (true) {
                delay(heartbeatIntervalMillis)
                send(WrenchPacketBuilder.heartbeat())
            }
        }
    }

    private fun closeSocketQuietly() {
        runCatching { socket?.close() }
        socket = null
    }
}
