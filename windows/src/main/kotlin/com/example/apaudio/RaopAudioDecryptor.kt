package com.example.apaudio

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal data class RaopAudioPacket(
    val sequenceNumber: Int,
    val timestamp: Long,
    val payloadType: Int,
    val decryptedPayload: ByteArray
)

internal class RaopAudioDecryptor(session: RaopAudioSession) {
    private val key = SecretKeySpec(session.aesKey.copyOf(), "AES")
    private val iv = IvParameterSpec(session.aesIv.copyOf())
    private val cipher = Cipher.getInstance("AES/CBC/NoPadding")

    fun decrypt(rtpPacket: ByteArray): RaopAudioPacket {
        require(rtpPacket.size >= RTP_FIXED_HEADER_BYTES) { "RTP packet is too short" }
        require(((rtpPacket[0].toInt() and 0xff) ushr 6) == 2) { "Unsupported RTP version" }

        val contributingSources = rtpPacket[0].toInt() and 0x0f
        var headerLength = RTP_FIXED_HEADER_BYTES + contributingSources * 4
        require(rtpPacket.size >= headerLength) { "Truncated RTP CSRC list" }

        val hasExtension = rtpPacket[0].toInt() and 0x10 != 0
        if (hasExtension) {
            require(rtpPacket.size >= headerLength + 4) { "Truncated RTP extension" }
            val extensionWords = unsignedShort(rtpPacket, headerLength + 2)
            headerLength += 4 + extensionWords * 4
            require(rtpPacket.size >= headerLength) { "Truncated RTP extension data" }
        }

        val encryptedPayload = rtpPacket.copyOfRange(headerLength, rtpPacket.size)
        val encryptedLength = encryptedPayload.size and -AES_BLOCK_BYTES
        val decryptedPayload = ByteArray(encryptedPayload.size)
        if (encryptedLength > 0) {
            cipher.init(Cipher.DECRYPT_MODE, key, iv)
            val decryptedBlocks = cipher.doFinal(encryptedPayload, 0, encryptedLength)
            decryptedBlocks.copyInto(decryptedPayload)
        }
        encryptedPayload.copyInto(
            decryptedPayload,
            destinationOffset = encryptedLength,
            startIndex = encryptedLength
        )

        return RaopAudioPacket(
            sequenceNumber = unsignedShort(rtpPacket, 2),
            timestamp = unsignedInt(rtpPacket, 4),
            payloadType = rtpPacket[1].toInt() and 0x7f,
            decryptedPayload = decryptedPayload
        )
    }

    private fun unsignedShort(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or
            (bytes[offset + 1].toInt() and 0xff)

    private fun unsignedInt(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)

    private companion object {
        const val RTP_FIXED_HEADER_BYTES = 12
        const val AES_BLOCK_BYTES = 16
    }
}
