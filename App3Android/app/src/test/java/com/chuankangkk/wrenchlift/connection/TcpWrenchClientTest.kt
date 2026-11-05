package com.chuankangkk.wrenchlift.connection

import com.chuankangkk.wrenchlift.protocol.WrenchPacket
import com.chuankangkk.wrenchlift.protocol.WrenchPacketBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket

class TcpWrenchClientTest {
    @Test
    fun sendsCommandAndReceivesSplitPacketOverTcp() = runBlocking {
        val client = TcpWrenchClient(connectTimeoutMillis = 1_000, heartbeatIntervalMillis = 60_000)
        val command = WrenchPacketBuilder.querySn()
        val reply = WrenchPacketBuilder.heartbeat()
        val receivedCommand = CompletableDeferred<ByteArray>()
        val serverSocket = ServerSocket(0)
        val serverJob = launch(Dispatchers.IO) {
            serverSocket.use { server ->
                server.accept().use { socket ->
                    receivedCommand.complete(socket.getInputStream().readExactly(command.toFrame().size))
                    val replyFrame = reply.toFrame()
                    val output = socket.getOutputStream()
                    output.write(replyFrame.copyOfRange(0, 4))
                    output.flush()
                    delay(20)
                    output.write(replyFrame.copyOfRange(4, replyFrame.size))
                    output.flush()
                }
            }
        }

        client.connect("127.0.0.1", serverSocket.localPort)
        val incoming = async(Dispatchers.IO) {
            withTimeout(2_000) { client.incomingPackets.first() }
        }
        client.send(command)

        assertArrayEquals(command.toFrame(), withTimeout(2_000) { receivedCommand.await() })
        assertEquals(WrenchPacket.FUNC_HEARTBEAT, incoming.await().functionCode)

        client.disconnect()
        serverJob.cancelAndJoin()
    }

    @Test
    fun disconnectUnblocksIdleReadLoop() = runBlocking {
        val client = TcpWrenchClient(connectTimeoutMillis = 1_000, heartbeatIntervalMillis = 60_000)
        val acceptedSocket = CompletableDeferred<Socket>()
        val serverSocket = ServerSocket(0)
        val serverJob = launch(Dispatchers.IO) {
            serverSocket.use { server ->
                server.accept().use { socket ->
                    acceptedSocket.complete(socket)
                    delay(10_000)
                }
            }
        }

        client.connect("127.0.0.1", serverSocket.localPort)
        withTimeout(2_000) { acceptedSocket.await() }
        withTimeout(2_000) { client.disconnect() }

        serverJob.cancelAndJoin()
    }
}

private fun InputStream.readExactly(length: Int): ByteArray {
    val output = ByteArray(length)
    var offset = 0
    while (offset < length) {
        val count = read(output, offset, length - offset)
        check(count >= 0) { "TCP 流提前关闭" }
        offset += count
    }
    return output
}
