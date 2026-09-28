package com.example.apaudio

import java.io.Closeable
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import kotlin.concurrent.thread

internal class RaopUdpPorts private constructor(
    internal val audioSocket: DatagramSocket,
    internal val controlSocket: DatagramSocket,
    internal val timingSocket: DatagramSocket
) : Closeable {
    val audioPort: Int get() = audioSocket.localPort
    val controlPort: Int get() = controlSocket.localPort
    val timingPort: Int get() = timingSocket.localPort

    fun startDiagnostics(
        audioPacketObserver: (packet: ByteArray) -> Unit,
        observer: (channel: String, packetNumber: Long, length: Int, header: ByteArray) -> Unit
    ) {
        startDiagnosticReceiver("audio", audioSocket, audioPacketObserver, observer)
        startDiagnosticReceiver("control", controlSocket, {}, observer)
        startDiagnosticReceiver("timing", timingSocket, {}, observer)
    }

    override fun close() {
        audioSocket.close()
        controlSocket.close()
        timingSocket.close()
    }

    private fun startDiagnosticReceiver(
        channel: String,
        socket: DatagramSocket,
        packetObserver: (packet: ByteArray) -> Unit,
        observer: (channel: String, packetNumber: Long, length: Int, header: ByteArray) -> Unit
    ) {
        thread(name = "raop-$channel-diagnostic", isDaemon = true) {
            val buffer = ByteArray(MAX_UDP_PACKET_BYTES)
            var packetNumber = 0L
            try {
                while (!socket.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    packetNumber += 1
                    packetObserver(buffer.copyOf(packet.length))
                    observer(
                        channel,
                        packetNumber,
                        packet.length,
                        buffer.copyOf(minOf(packet.length, RTP_HEADER_BYTES))
                    )
                }
            } catch (error: SocketException) {
                if (!socket.isClosed) throw error
            }
        }
    }

    companion object {
        private const val MAX_UDP_PACKET_BYTES = 65_535
        private const val RTP_HEADER_BYTES = 12

        fun open(localAddress: InetAddress): RaopUdpPorts {
            val opened = mutableListOf<DatagramSocket>()
            return try {
                fun bind(): DatagramSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(localAddress, 0))
                    opened += this
                }

                val audio = bind()
                val control = bind()
                val timing = bind()
                RaopUdpPorts(audio, control, timing)
            } catch (error: Exception) {
                opened.forEach { it.close() }
                throw error
            }
        }
    }
}
