package com.example.apaudio

internal enum class RaopPlaybackState {
    PLAYING,
    PAUSED
}

internal data class RaopMetadataUpdate(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMillis: Long? = null,
    val playbackState: RaopPlaybackState? = null
)

internal data class RaopProgress(
    val elapsedMillis: Long,
    val durationMillis: Long
)

internal data class RaopTextParameters(
    val volumeDb: Float? = null,
    val progress: RaopProgress? = null
)

/** Parses only the DMAP fields observed from the real iPhone used for APAudio verification. */
internal object RaopDmapParser {
    private const val HEADER_BYTES = 8

    fun parse(body: ByteArray): RaopMetadataUpdate {
        require(body.size >= HEADER_BYTES) { "DMAP body is too short" }
        val root = readEntry(body, 0, body.size)
        require(root.tag == "mlit") { "Expected observed DMAP mlit container, got ${root.tag}" }
        require(root.nextOffset == body.size) { "Trailing bytes after DMAP mlit container" }

        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMillis: Long? = null
        var playbackState: RaopPlaybackState? = null
        var offset = root.valueOffset
        while (offset < root.nextOffset) {
            val entry = readEntry(body, offset, root.nextOffset)
            when (entry.tag) {
                "minm" -> title = entry.utf8(body)
                "asar" -> artist = entry.utf8(body)
                "asal" -> album = entry.utf8(body)
                "astm" -> durationMillis = entry.unsignedValue(body)
                "caps" -> playbackState = when (entry.unsignedValue(body)) {
                    1L -> RaopPlaybackState.PLAYING
                    2L -> RaopPlaybackState.PAUSED
                    else -> null
                }
            }
            offset = entry.nextOffset
        }

        return RaopMetadataUpdate(
            title = title,
            artist = artist,
            album = album,
            durationMillis = durationMillis,
            playbackState = playbackState
        )
    }

    private fun readEntry(body: ByteArray, offset: Int, containerEnd: Int): DmapEntry {
        require(offset >= 0 && containerEnd <= body.size && offset <= containerEnd) {
            "Invalid DMAP container bounds"
        }
        require(containerEnd - offset >= HEADER_BYTES) { "Truncated DMAP entry header" }
        val tag = body.copyOfRange(offset, offset + 4).toString(Charsets.US_ASCII)
        val length = readUnsigned(body, offset + 4, 4)
        require(length <= Int.MAX_VALUE.toLong()) { "DMAP entry is too large" }
        val valueOffset = offset + HEADER_BYTES
        val nextOffset = valueOffset.toLong() + length
        require(nextOffset <= containerEnd.toLong()) { "DMAP entry $tag exceeds its container" }
        return DmapEntry(tag, valueOffset, length.toInt(), nextOffset.toInt())
    }

    private fun readUnsigned(body: ByteArray, offset: Int, length: Int): Long {
        require(length in 1..8 && offset >= 0 && offset + length <= body.size) {
            "Invalid DMAP unsigned integer"
        }
        var value = 0L
        repeat(length) { index ->
            value = (value shl 8) or (body[offset + index].toLong() and 0xff)
        }
        return value
    }

    private data class DmapEntry(
        val tag: String,
        val valueOffset: Int,
        val length: Int,
        val nextOffset: Int
    ) {
        fun utf8(body: ByteArray): String =
            body.copyOfRange(valueOffset, valueOffset + length).toString(Charsets.UTF_8)

        fun unsignedValue(body: ByteArray): Long = readUnsigned(body, valueOffset, length)
    }
}

internal object RaopTextParametersParser {
    private const val UINT32_MASK = 0xffff_ffffL

    fun parse(body: ByteArray, sampleRate: Int): RaopTextParameters {
        require(sampleRate > 0) { "Sample rate must be positive" }
        var volumeDb: Float? = null
        var progress: RaopProgress? = null
        body.toString(Charsets.UTF_8).lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("volume:", ignoreCase = true) -> {
                    volumeDb = line.substringAfter(':').trim().toFloatOrNull()?.takeIf { it.isFinite() }
                }
                line.startsWith("progress:", ignoreCase = true) -> {
                    progress = parseProgress(line.substringAfter(':').trim(), sampleRate)
                }
            }
        }
        return RaopTextParameters(volumeDb = volumeDb, progress = progress)
    }

    private fun parseProgress(value: String, sampleRate: Int): RaopProgress? {
        val parts = value.split('/')
        if (parts.size != 3) return null
        val start = parts[0].trim().toLongOrNull()?.takeIf(::isUint32) ?: return null
        val current = parts[1].trim().toLongOrNull()?.takeIf(::isUint32) ?: return null
        val end = parts[2].trim().toLongOrNull()?.takeIf(::isUint32) ?: return null
        val durationTicks = unsignedDistance(start, end)
        val elapsedTicks = unsignedDistance(start, current).coerceAtMost(durationTicks)
        return RaopProgress(
            elapsedMillis = elapsedTicks * 1_000L / sampleRate,
            durationMillis = durationTicks * 1_000L / sampleRate
        )
    }

    private fun isUint32(value: Long): Boolean = value in 0..UINT32_MASK

    private fun unsignedDistance(start: Long, end: Long): Long = (end - start) and UINT32_MASK
}
