package com.example.apaudio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsSenderDiagnosticsTest {
    @Test
    fun `sender diagnostics identify macOS without logging remote token values`() {
        val request = RtspRequest(
            requestLine = "OPTIONS * RTSP/1.0",
            headers = linkedMapOf(
                "User-Agent" to "iTunes/12.9 (Macintosh; OS X 10.15.7)",
                "Client-Instance" to "CLIENT123",
                "DACP-ID" to "DACP123",
                "Active-Remote" to "1986535575"
            ),
            body = byteArrayOf()
        )

        val description = WindowsSenderDiagnostics.describeSender(request)

        assertTrue(description.contains("sender=macOS"))
        assertTrue(description.contains("clientInstance=true"))
        assertTrue(description.contains("dacpId=true"))
        assertTrue(description.contains("activeRemote=true"))
        assertFalse(description.contains("1986535575"))
        assertFalse(description.contains("DACP123"))
    }

    @Test
    fun `text parameter diagnostics report keys without values`() {
        val request = RtspRequest(
            requestLine = "SET_PARAMETER rtsp://example RTSP/1.0",
            headers = linkedMapOf("Content-Type" to "text/parameters; charset=utf-8"),
            body = "volume: -20.0\r\nprogress: 1/2/3\r\n".toByteArray()
        )

        val description = WindowsSenderDiagnostics.describeSetParameter(request)

        assertTrue(description.contains("keys=volume,progress"))
        assertFalse(description.contains("-20.0"))
        assertFalse(description.contains("1/2/3"))
    }

    @Test
    fun `DMAP diagnostics expose only root tag and size`() {
        val body = byteArrayOf(
            'm'.code.toByte(), 'l'.code.toByte(), 'i'.code.toByte(), 't'.code.toByte(),
            0, 0, 0, 0
        )
        val request = RtspRequest(
            requestLine = "SET_PARAMETER rtsp://example RTSP/1.0",
            headers = linkedMapOf("Content-Type" to "application/x-dmap-tagged"),
            body = body
        )

        assertEquals("mlit", WindowsSenderDiagnostics.dmapRootTag(body))
        assertTrue(WindowsSenderDiagnostics.describeSetParameter(request).contains("root=mlit"))
    }

    @Test
    fun `parsed metadata diagnostics report field presence without media contents`() {
        val metadata = RaopMetadataUpdate(
            title = "private title",
            artist = "private artist",
            durationMillis = 123_456L,
            playbackState = RaopPlaybackState.PLAYING
        )

        val description = WindowsSenderDiagnostics.describeMetadata(metadata)

        assertEquals("fields=title,artist,duration,playbackState", description)
        assertFalse(description.contains("private title"))
        assertFalse(description.contains("private artist"))
        assertFalse(description.contains("123456"))
    }
}
