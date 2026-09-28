package com.example.apaudio

import java.io.Closeable
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.pow
import kotlin.math.roundToInt

/** Windows/JVM PCM sink using the default Java Sound output device. */
internal class WindowsRaopAudioOutput(
    private val sampleRate: Int,
    private val channels: Int,
    frameLength: Int
) : Closeable {
    companion object {
        private const val BYTES_PER_SAMPLE = 2
        private const val PACKETS_OF_BUFFERING = 64
        private const val MIN_BUFFER_BYTES = 16 * 1024
        private const val MUTED_VOLUME_DB = -144f
    }

    private val line: SourceDataLine
    private var started = false
    private var gain = 1f

    init {
        require(channels == 2) { "Only stereo PCM output is supported" }

        val format = AudioFormat(
            sampleRate.toFloat(),
            16,
            channels,
            true,
            false
        )
        val info = DataLine.Info(SourceDataLine::class.java, format)
        check(AudioSystem.isLineSupported(info)) {
            "Windows default audio device does not support ${sampleRate}Hz stereo PCM16"
        }

        val packetBytes = frameLength * channels * BYTES_PER_SAMPLE
        val bufferBytes = maxOf(MIN_BUFFER_BYTES, packetBytes * PACKETS_OF_BUFFERING)
        line = AudioSystem.getLine(info) as SourceDataLine
        line.open(format, bufferBytes)
    }

    @Synchronized
    fun start() {
        if (!started) {
            line.start()
            started = true
        }
    }

    @Synchronized
    fun write(frame: PcmFrame) {
        require(frame.sampleRate == sampleRate && frame.channels == channels) {
            "PCM frame format changed during the session"
        }

        val bytes = ByteArray(frame.samples.size * BYTES_PER_SAMPLE)
        frame.samples.forEachIndexed { index, rawSample ->
            val sample = if (gain == 1f) {
                rawSample
            } else {
                (rawSample.toInt() * gain)
                    .roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
            val value = sample.toInt()
            bytes[index * 2] = (value and 0xff).toByte()
            bytes[index * 2 + 1] = ((value ushr 8) and 0xff).toByte()
        }

        var offset = 0
        while (offset < bytes.size) {
            val written = line.write(bytes, offset, bytes.size - offset)
            check(written > 0) { "Java Sound write failed: $written" }
            offset += written
        }
    }

    @Synchronized
    fun flush() {
        if (started) {
            line.stop()
            line.flush()
            line.start()
        } else {
            line.flush()
        }
    }

    @Synchronized
    fun setVolumeDb(volumeDb: Float) {
        gain = if (volumeDb <= MUTED_VOLUME_DB) {
            0f
        } else {
            10.0.pow(volumeDb.toDouble() / 20.0).toFloat().coerceIn(0f, 1f)
        }
    }

    @Synchronized
    override fun close() {
        runCatching { if (started) line.stop() }
        runCatching { line.flush() }
        line.close()
        started = false
    }
}
