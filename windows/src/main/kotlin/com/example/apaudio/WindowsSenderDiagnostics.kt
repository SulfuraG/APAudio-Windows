package com.example.apaudio

import java.nio.charset.StandardCharsets

/**
 * Privacy-conscious diagnostics for comparing AirPlay sender behaviour.
 *
 * It deliberately records header presence rather than DACP/Active-Remote values and never dumps
 * metadata/artwork payload bytes. This keeps packaged logs useful for sender compatibility work
 * without persisting remote-control tokens or the user's media contents.
 */
internal object WindowsSenderDiagnostics {
    fun describeSender(request: RtspRequest): String {
        val userAgent = request.header("User-Agent")?.takeIf { it.isNotBlank() } ?: "unknown"
        val family = classifyUserAgent(userAgent)
        return "sender=$family userAgent='$userAgent' " +
            "clientInstance=${request.header("Client-Instance") != null} " +
            "dacpId=${request.header("DACP-ID") != null} " +
            "activeRemote=${request.header("Active-Remote") != null}"
    }

    fun describeSetParameter(request: RtspRequest): String {
        val contentType = normalizedContentType(request)
        return when (contentType) {
            "text/parameters" -> {
                val keys = textParameterKeys(request.body)
                "contentType=$contentType bytes=${request.body.size} keys=${keys.joinToString(",").ifEmpty { "none" }}"
            }
            "application/x-dmap-tagged" ->
                "contentType=$contentType bytes=${request.body.size} root=${dmapRootTag(request.body)}"
            "image/jpeg" -> "contentType=$contentType bytes=${request.body.size}"
            "image/none" -> "contentType=$contentType bytes=${request.body.size}"
            else -> "contentType=${contentType.ifEmpty { "unknown" }} bytes=${request.body.size}"
        }
    }

    fun normalizedContentType(request: RtspRequest): String =
        request.header("Content-Type")
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()

    fun describeMetadata(metadata: RaopMetadataUpdate): String {
        val fields = buildList {
            if (metadata.title != null) add("title")
            if (metadata.artist != null) add("artist")
            if (metadata.album != null) add("album")
            if (metadata.durationMillis != null) add("duration")
            if (metadata.playbackState != null) add("playbackState")
        }
        return "fields=${fields.joinToString(",").ifEmpty { "none" }}"
    }

    internal fun dmapRootTag(body: ByteArray): String {
        if (body.size < 4) return "none"
        val tag = body.copyOfRange(0, 4).toString(StandardCharsets.US_ASCII)
        return if (tag.all { character -> character.code in 0x20..0x7e }) tag else "non-ascii"
    }

    internal fun textParameterKeys(body: ByteArray): List<String> =
        body.toString(StandardCharsets.UTF_8)
            .lineSequence()
            .map { line -> line.substringBefore(':').trim().lowercase() }
            .filter { key -> key.isNotEmpty() }
            .distinct()
            .toList()

    private fun classifyUserAgent(userAgent: String): String = when {
        userAgent.contains("Macintosh", ignoreCase = true) ||
            userAgent.contains("Mac OS X", ignoreCase = true) ||
            userAgent.startsWith("iTunes/", ignoreCase = true) -> "macOS"
        userAgent.startsWith("AirPlay/", ignoreCase = true) -> "AirPlay"
        else -> "unknown"
    }
}
