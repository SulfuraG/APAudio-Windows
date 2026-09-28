package com.example.apaudio

internal data class RaopClientTransport(
    val controlPort: Int,
    val timingPort: Int
)

internal object RaopTransport {
    fun parseClient(header: String): RaopClientTransport {
        require(header.substringBefore(';').equals("RTP/AVP/UDP", ignoreCase = true)) {
            "Only RTP/AVP/UDP is supported"
        }

        val parameters = header.split(';')
            .drop(1)
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) null else {
                    part.substring(0, separator).trim().lowercase() to
                        part.substring(separator + 1).trim()
                }
            }
            .toMap()

        return RaopClientTransport(
            controlPort = parameters.requirePort("control_port"),
            timingPort = parameters.requirePort("timing_port")
        )
    }

    fun serverHeader(ports: RaopUdpPorts): String =
        "RTP/AVP/UDP;unicast;interleaved=0-1;mode=record;" +
            "control_port=${ports.controlPort};" +
            "timing_port=${ports.timingPort};" +
            "server_port=${ports.audioPort}"

    private fun Map<String, String>.requirePort(name: String): Int {
        val port = get(name)?.toIntOrNull()
        require(port != null && port in 1..65535) { "Missing or invalid $name" }
        return port
    }
}
