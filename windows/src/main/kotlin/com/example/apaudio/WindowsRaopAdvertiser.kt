package com.example.apaudio

import java.io.Closeable
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/** Advertises the Windows receiver as an AirPlay 1 / RAOP speaker over mDNS. */
internal class WindowsRaopAdvertiser(
    private val deviceName: String,
    private val rtspPort: Int
) : Closeable {
    companion object {
        internal const val DEVICE_ID = "BA5E1AD10002"
    }

    private var jmDns: JmDNS? = null

    fun start(): InetAddress {
        check(jmDns == null) { "mDNS advertiser is already running" }
        val address = chooseLanAddress()
        val instance = JmDNS.create(address, "APAudio-Windows")
        val attributes = linkedMapOf(
            "txtvers" to "1",
            "ch" to "2",
            "cn" to "0,1",
            "et" to "0,1",
            "sv" to "false",
            "da" to "true",
            "sr" to "44100",
            "ss" to "16",
            "pw" to "false",
            "vn" to "65537",
            "tp" to "UDP",
            "md" to "0,1,2",
            "vs" to "105.1",
            "am" to "ShairportSync",
            "sf" to "0x4"
        )
        val service = ServiceInfo.create(
            "_raop._tcp.local.",
            "$DEVICE_ID@$deviceName",
            rtspPort,
            0,
            0,
            attributes
        )
        instance.registerService(service)
        jmDns = instance
        println("mDNS registered on ${address.hostAddress}: ${service.name}")
        return address
    }

    override fun close() {
        val instance = jmDns ?: return
        jmDns = null
        runCatching { instance.unregisterAllServices() }
        runCatching { instance.close() }
    }

    private fun chooseLanAddress(): Inet4Address {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        val candidates = interfaces.asSequence()
            .filter { network ->
                runCatching {
                    network.isUp &&
                        !network.isLoopback &&
                        !network.isVirtual &&
                        network.supportsMulticast()
                }.getOrDefault(false)
            }
            .flatMap { network -> Collections.list(network.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .filter { address -> !address.isLoopbackAddress && !address.isLinkLocalAddress }
            .toList()

        return candidates.firstOrNull()
            ?: error("No active multicast-capable IPv4 LAN interface was found")
    }
}
