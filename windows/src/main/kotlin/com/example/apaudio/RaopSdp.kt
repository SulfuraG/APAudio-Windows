package com.example.apaudio

internal data class AlacFormat(
    val frameLength: Int,
    val compatibleVersion: Int,
    val bitDepth: Int,
    val pb: Int,
    val mb: Int,
    val kb: Int,
    val channels: Int,
    val maxRun: Int,
    val maxFrameBytes: Int,
    val averageBitRate: Int,
    val sampleRate: Int
) {
    fun asFmtpArray(payloadType: Int = 96): IntArray = intArrayOf(
        payloadType,
        frameLength,
        compatibleVersion,
        bitDepth,
        pb,
        mb,
        kb,
        channels,
        maxRun,
        maxFrameBytes,
        averageBitRate,
        sampleRate
    )
}

internal data class RaopSdp(
    val codec: String,
    val format: AlacFormat,
    val encryptedAesKeyBase64: String,
    val aesIvBase64: String
)

internal object RaopSdpParser {
    fun parse(body: String): RaopSdp {
        val lines = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val rtpMap = lines.requireAttribute("a=rtpmap:")
        val codec = rtpMap.substringAfter(' ', missingDelimiterValue = "").trim()
        require(codec.equals("AppleLossless", ignoreCase = true)) {
            "Unsupported RAOP codec: $codec"
        }

        val fmtpValues = lines.requireAttribute("a=fmtp:")
            .substringAfter(' ', missingDelimiterValue = "")
            .trim()
            .split(Regex("\\s+"))
            .map { it.toInt() }
        require(fmtpValues.size == 11) { "Expected 11 ALAC fmtp values" }

        return RaopSdp(
            codec = codec,
            format = AlacFormat(
                frameLength = fmtpValues[0],
                compatibleVersion = fmtpValues[1],
                bitDepth = fmtpValues[2],
                pb = fmtpValues[3],
                mb = fmtpValues[4],
                kb = fmtpValues[5],
                channels = fmtpValues[6],
                maxRun = fmtpValues[7],
                maxFrameBytes = fmtpValues[8],
                averageBitRate = fmtpValues[9],
                sampleRate = fmtpValues[10]
            ),
            encryptedAesKeyBase64 = lines.requireAttribute("a=rsaaeskey:")
                .substringAfter(':'),
            aesIvBase64 = lines.requireAttribute("a=aesiv:").substringAfter(':')
        )
    }

    private fun List<String>.requireAttribute(prefix: String): String =
        firstOrNull { it.startsWith(prefix, ignoreCase = true) }
            ?: throw IllegalArgumentException("Missing SDP attribute $prefix")
}

internal data class RaopAudioSession(
    val codec: String,
    val format: AlacFormat,
    val aesKey: ByteArray,
    val aesIv: ByteArray
) {
    init {
        require(aesKey.size == 16) { "RAOP AES key must be 16 bytes" }
        require(aesIv.size == 16) { "RAOP AES IV must be 16 bytes" }
    }
}
