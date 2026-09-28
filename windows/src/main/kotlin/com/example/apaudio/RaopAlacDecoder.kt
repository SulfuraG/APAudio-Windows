package com.example.apaudio

import com.example.apaudio.alac.AlacDecoder

internal data class PcmFrame(
    val samples: ShortArray,
    val channels: Int,
    val sampleRate: Int
)

internal class RaopAlacDecoder(private val format: AlacFormat) {
    companion object {
        // The vendored bit reader performs a small speculative look-ahead at the
        // end of valid ALAC frames. Keep that implementation isolated by giving
        // it zero-filled guard bytes in this adapter.
        private const val INPUT_GUARD_BYTES = 8
    }

    private val decoder = AlacDecoder(format.asFmtpArray())
    private val output = IntArray(format.frameLength * format.channels)

    fun decode(encodedFrame: ByteArray): PcmFrame {
        val guardedFrame = encodedFrame.copyOf(encodedFrame.size + INPUT_GUARD_BYTES)
        val outputBytes = decoder.decodeFrame(guardedFrame, output)
        val bytesPerSample = format.bitDepth / 8
        require(outputBytes % bytesPerSample == 0) { "ALAC decoder returned partial PCM sample" }
        val sampleCount = outputBytes / bytesPerSample
        require(sampleCount in 0..output.size) { "ALAC decoder returned too many samples" }

        return PcmFrame(
            samples = ShortArray(sampleCount) { output[it].toShort() },
            channels = format.channels,
            sampleRate = format.sampleRate
        )
    }
}
