package com.example.apaudio

import java.util.ArrayDeque
import java.util.Locale

internal data class RtpSequenceSnapshot(
    val packetCount: Long,
    val gapEvents: Long,
    val missingDetected: Long,
    val lateRecovered: Long,
    val missingOutstanding: Int,
    val outOfOrder: Long,
    val duplicates: Long
)

/** Observes RTP sequence numbers without changing packet delivery or ordering. */
internal class RtpSequenceTracker(
    private val recentWindowSize: Int = 8_192
) {
    private var highestExtendedSequence: Long? = null
    private var packetCount = 0L
    private var gapEvents = 0L
    private var missingDetected = 0L
    private var lateRecovered = 0L
    private var outOfOrder = 0L
    private var duplicates = 0L
    private val missingSequences = HashSet<Long>()
    private val recentSequences = HashSet<Long>()
    private val recentOrder = ArrayDeque<Long>()

    init {
        require(recentWindowSize > 0) { "Recent RTP window must be positive" }
    }

    @Synchronized
    fun observe(sequenceNumber: Int) {
        require(sequenceNumber in 0..0xffff) { "RTP sequence number must be unsigned 16-bit" }
        packetCount += 1

        val highest = highestExtendedSequence
        val extended = if (highest == null) {
            sequenceNumber.toLong()
        } else {
            extendNear(sequenceNumber, highest)
        }

        if (!recentSequences.add(extended)) {
            duplicates += 1
            return
        }
        recentOrder.addLast(extended)
        while (recentOrder.size > recentWindowSize) {
            recentSequences.remove(recentOrder.removeFirst())
        }

        if (highest == null) {
            highestExtendedSequence = extended
            return
        }

        if (extended > highest) {
            val advance = extended - highest
            if (advance > 1) {
                gapEvents += 1
                missingDetected += advance - 1
                var missing = highest + 1
                while (missing < extended) {
                    missingSequences += missing
                    missing += 1
                }
            }
            highestExtendedSequence = extended
        } else {
            outOfOrder += 1
            if (missingSequences.remove(extended)) {
                lateRecovered += 1
            }
        }
    }

    @Synchronized
    fun snapshot(): RtpSequenceSnapshot = RtpSequenceSnapshot(
        packetCount = packetCount,
        gapEvents = gapEvents,
        missingDetected = missingDetected,
        lateRecovered = lateRecovered,
        missingOutstanding = missingSequences.size,
        outOfOrder = outOfOrder,
        duplicates = duplicates
    )

    private fun extendNear(sequenceNumber: Int, reference: Long): Long {
        var candidate = (reference and -0x1_0000L) or sequenceNumber.toLong()
        val delta = candidate - reference
        if (delta > 0x7fffL) {
            candidate -= 0x1_0000L
        } else if (delta < -0x8000L) {
            candidate += 0x1_0000L
        }
        return candidate
    }
}

internal data class RaopStabilitySnapshot(
    val sessionId: Long,
    val elapsedMillis: Long,
    val audioSpanMillis: Long,
    val udpAudioPackets: Long,
    val controlPackets: Long,
    val timingPackets: Long,
    val rtp: RtpSequenceSnapshot,
    val decryptFailures: Long,
    val decodedPackets: Long,
    val decodeFailures: Long,
    val writtenPackets: Long,
    val pcmSamples: Long,
    val audioTrackWriteFailures: Long,
    val audioTrackWriteStalls: Long,
    val maxDecodeNanos: Long,
    val maxWriteNanos: Long,
    val maxPacketIntervalNanos: Long,
    val recordRequests: Long,
    val flushRequests: Long,
    val volumeChanges: Long
) {
    fun toLogFields(): String = String.format(
        Locale.US,
        "session=%d elapsedMs=%d audioSpanMs=%d udpAudioPackets=%d rtpPackets=%d " +
            "sequenceGapEvents=%d sequenceMissing=%d sequenceRecovered=%d " +
            "sequenceOutstanding=%d outOfOrder=%d duplicates=%d " +
            "decryptFailures=%d alacDecodedPackets=%d alacDecodeFailures=%d " +
            "audioTrackWrittenPackets=%d pcmSamples=%d audioTrackWriteFailures=%d " +
            "audioTrackWriteStalls=%d maxDecodeMs=%.3f maxWriteMs=%.3f " +
            "maxPacketIntervalMs=%.3f controlPackets=%d timingPackets=%d " +
            "recordRequests=%d flushRequests=%d volumeChanges=%d",
        sessionId,
        elapsedMillis,
        audioSpanMillis,
        udpAudioPackets,
        rtp.packetCount,
        rtp.gapEvents,
        rtp.missingDetected,
        rtp.lateRecovered,
        rtp.missingOutstanding,
        rtp.outOfOrder,
        rtp.duplicates,
        decryptFailures,
        decodedPackets,
        decodeFailures,
        writtenPackets,
        pcmSamples,
        audioTrackWriteFailures,
        audioTrackWriteStalls,
        maxDecodeNanos / 1_000_000.0,
        maxWriteNanos / 1_000_000.0,
        maxPacketIntervalNanos / 1_000_000.0,
        controlPackets,
        timingPackets,
        recordRequests,
        flushRequests,
        volumeChanges
    )
}

internal class RaopStabilityMetrics(
    val sessionId: Long,
    private val nanoTime: () -> Long = System::nanoTime,
    private val writeStallThresholdNanos: Long = 100_000_000L
) {
    private val sequenceTracker = RtpSequenceTracker()
    private val startedNanos = nanoTime()
    private var firstAudioNanos: Long? = null
    private var lastAudioNanos: Long? = null
    private var udpAudioPackets = 0L
    private var controlPackets = 0L
    private var timingPackets = 0L
    private var decryptFailures = 0L
    private var decodedPackets = 0L
    private var decodeFailures = 0L
    private var writtenPackets = 0L
    private var pcmSamples = 0L
    private var audioTrackWriteFailures = 0L
    private var audioTrackWriteStalls = 0L
    private var maxDecodeNanos = 0L
    private var maxWriteNanos = 0L
    private var maxPacketIntervalNanos = 0L
    private var recordRequests = 0L
    private var flushRequests = 0L
    private var volumeChanges = 0L

    @Synchronized
    fun recordAudioDatagram() {
        val now = nanoTime()
        val previous = lastAudioNanos
        if (previous != null) {
            maxPacketIntervalNanos = maxOf(maxPacketIntervalNanos, now - previous)
        }
        if (firstAudioNanos == null) firstAudioNanos = now
        lastAudioNanos = now
        udpAudioPackets += 1
    }

    fun recordRtp(sequenceNumber: Int) {
        sequenceTracker.observe(sequenceNumber)
    }

    @Synchronized
    fun recordChannelPacket(channel: String) {
        when (channel) {
            "control" -> controlPackets += 1
            "timing" -> timingPackets += 1
        }
    }

    @Synchronized
    fun recordDecryptFailure(): Long = ++decryptFailures

    @Synchronized
    fun recordDecodeSuccess(durationNanos: Long) {
        decodedPackets += 1
        maxDecodeNanos = maxOf(maxDecodeNanos, durationNanos)
    }

    @Synchronized
    fun recordDecodeFailure(durationNanos: Long): Long {
        decodeFailures += 1
        maxDecodeNanos = maxOf(maxDecodeNanos, durationNanos)
        return decodeFailures
    }

    @Synchronized
    fun recordAudioTrackWriteSuccess(durationNanos: Long, sampleCount: Int) {
        writtenPackets += 1
        pcmSamples += sampleCount
        recordWriteDuration(durationNanos)
    }

    @Synchronized
    fun recordAudioTrackWriteFailure(durationNanos: Long): Long {
        audioTrackWriteFailures += 1
        recordWriteDuration(durationNanos)
        return audioTrackWriteFailures
    }

    @Synchronized
    fun recordRecordRequest() {
        recordRequests += 1
    }

    @Synchronized
    fun recordFlushRequest() {
        flushRequests += 1
    }

    @Synchronized
    fun recordVolumeChange() {
        volumeChanges += 1
    }

    fun snapshot(): RaopStabilitySnapshot {
        val now = nanoTime()
        val rtpSnapshot = sequenceTracker.snapshot()
        return synchronized(this) {
            RaopStabilitySnapshot(
                sessionId = sessionId,
                elapsedMillis = nanosToMillis(now - startedNanos),
                audioSpanMillis = if (firstAudioNanos != null && lastAudioNanos != null) {
                    nanosToMillis(lastAudioNanos!! - firstAudioNanos!!)
                } else {
                    0L
                },
                udpAudioPackets = udpAudioPackets,
                controlPackets = controlPackets,
                timingPackets = timingPackets,
                rtp = rtpSnapshot,
                decryptFailures = decryptFailures,
                decodedPackets = decodedPackets,
                decodeFailures = decodeFailures,
                writtenPackets = writtenPackets,
                pcmSamples = pcmSamples,
                audioTrackWriteFailures = audioTrackWriteFailures,
                audioTrackWriteStalls = audioTrackWriteStalls,
                maxDecodeNanos = maxDecodeNanos,
                maxWriteNanos = maxWriteNanos,
                maxPacketIntervalNanos = maxPacketIntervalNanos,
                recordRequests = recordRequests,
                flushRequests = flushRequests,
                volumeChanges = volumeChanges
            )
        }
    }

    private fun recordWriteDuration(durationNanos: Long) {
        maxWriteNanos = maxOf(maxWriteNanos, durationNanos)
        if (durationNanos >= writeStallThresholdNanos) {
            audioTrackWriteStalls += 1
        }
    }

    private fun nanosToMillis(nanos: Long): Long = nanos / 1_000_000L
}
