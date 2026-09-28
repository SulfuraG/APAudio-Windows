package com.example.apaudio

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.charset.StandardCharsets

internal data class RtspRequest(
    val requestLine: String,
    val headers: LinkedHashMap<String, String>,
    val body: ByteArray
) {
    val method: String get() = requestLine.substringBefore(' ')

    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

internal class RtspRequestReader(private val input: InputStream) {
    fun read(): RtspRequest? {
        var requestLine: String
        do {
            requestLine = readLine() ?: return null
        } while (requestLine.isEmpty())

        val headers = LinkedHashMap<String, String>()
        while (true) {
            val line = readLine() ?: throw EOFException("EOF while reading RTSP headers")
            if (line.isEmpty()) break
            val separator = line.indexOf(':')
            require(separator > 0) { "Malformed RTSP header: $line" }
            headers[line.substring(0, separator).trim()] =
                line.substring(separator + 1).trim()
        }

        val contentLength = headers.entries
            .firstOrNull { it.key.equals("Content-Length", ignoreCase = true) }
            ?.value
            ?.toIntOrNull()
            ?: 0
        require(contentLength in 0..MAX_BODY_BYTES) {
            "Invalid RTSP Content-Length: $contentLength"
        }

        return RtspRequest(requestLine, headers, readExactly(contentLength))
    }

    private fun readLine(): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value < 0) {
                return if (bytes.size() == 0) null else
                    bytes.toString(StandardCharsets.ISO_8859_1.name())
            }
            if (value == '\n'.code) {
                return bytes.toString(StandardCharsets.ISO_8859_1.name())
            }
            if (value != '\r'.code) bytes.write(value)
            require(bytes.size() <= MAX_LINE_BYTES) { "RTSP line is too long" }
        }
    }

    private fun readExactly(length: Int): ByteArray {
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(body, offset, length - offset)
            if (read < 0) throw EOFException("EOF after $offset of $length RTSP body bytes")
            offset += read
        }
        return body
    }

    private companion object {
        const val MAX_LINE_BYTES = 16 * 1024
        const val MAX_BODY_BYTES = 16 * 1024 * 1024
    }
}
