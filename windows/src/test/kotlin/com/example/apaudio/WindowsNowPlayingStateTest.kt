package com.example.apaudio

import java.awt.Color
import java.awt.image.BufferedImage
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class WindowsNowPlayingStateTest {
    @Test
    fun realSessionFieldsFlowIntoSnapshotAndResetOnDisconnect() {
        var nowNanos = 1_000_000_000L
        val state = WindowsNowPlayingState { nowNanos }
        val artwork = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)

        state.receiverReady(InetAddress.getByName("192.168.1.5"))
        assertEquals(WindowsReceiverStatus.WAITING, state.snapshot().status)

        state.connectionOpened(7L)
        state.playbackStarted(7L)
        state.applyMetadata(
            7L,
            RaopMetadataUpdate(
                title = "Title",
                artist = "Artist",
                album = "Album",
                durationMillis = 120_000L,
                playbackState = RaopPlaybackState.PLAYING
            )
        )
        state.updateProgress(7L, RaopProgress(elapsedMillis = 10_000L, durationMillis = 120_000L))
        state.updateVolume(7L, -18.125f)
        state.updateArtwork(7L, artwork)

        nowNanos += 2_000_000_000L
        val playing = state.snapshot()
        assertEquals(WindowsReceiverStatus.PLAYING, playing.status)
        assertEquals("Title", playing.title)
        assertEquals("Artist", playing.artist)
        assertEquals("Album", playing.album)
        assertEquals(12_000L, playing.currentElapsedMillis(nowNanos))
        assertEquals(-18.125f, playing.volumeDb)
        assertSame(artwork, playing.artwork)

        state.connectionClosed(7L)
        val waiting = state.snapshot()
        assertEquals(WindowsReceiverStatus.WAITING, waiting.status)
        assertEquals("192.168.1.5", waiting.advertisedAddress)
        assertNull(waiting.title)
        assertNull(waiting.artwork)
        assertNull(waiting.volumeDb)
    }

    @Test
    fun staleConnectionCannotOverwriteTheCurrentSession() {
        val state = WindowsNowPlayingState()
        state.receiverReady(InetAddress.getByName("192.168.1.5"))
        state.connectionOpened(1L)
        state.connectionOpened(2L)

        state.applyMetadata(1L, RaopMetadataUpdate(title = "Stale"))
        state.connectionClosed(1L)

        assertEquals(WindowsReceiverStatus.CONNECTED, state.snapshot().status)
        assertNull(state.snapshot().title)

        state.applyMetadata(2L, RaopMetadataUpdate(title = "Current"))
        assertEquals("Current", state.snapshot().title)
    }

    @Test
    fun pausingPreservesTheEstimatedPlaybackPosition() {
        var nowNanos = 5_000_000_000L
        val state = WindowsNowPlayingState { nowNanos }
        state.receiverReady(InetAddress.getByName("192.168.1.5"))
        state.connectionOpened(3L)
        state.playbackStarted(3L)
        state.updateProgress(3L, RaopProgress(elapsedMillis = 20_000L, durationMillis = 60_000L))

        nowNanos += 3_000_000_000L
        state.applyMetadata(
            3L,
            RaopMetadataUpdate(playbackState = RaopPlaybackState.PAUSED)
        )

        assertEquals(WindowsReceiverStatus.CONNECTED, state.snapshot().status)
        assertEquals(23_000L, state.snapshot().currentElapsedMillis(nowNanos))
    }

    @Test
    fun `new media metadata resets locally estimated progress`() {
        var nowNanos = 10_000_000_000L
        val state = playingState(nowNanos) { nowNanos }
        state.applyMetadata(1L, metadata(title = "First", durationMillis = 180_000L))
        state.updateProgress(1L, RaopProgress(elapsedMillis = 45_000L, durationMillis = 180_000L))

        nowNanos += 5_000_000_000L
        state.applyMetadata(1L, metadata(title = "Second", durationMillis = 240_000L))

        val snapshot = state.snapshot()
        assertEquals(0L, snapshot.elapsedMillis)
        assertEquals(0L, snapshot.currentElapsedMillis(nowNanos))
        assertEquals(240_000L, snapshot.durationMillis)
        assertEquals(nowNanos, snapshot.progressAnchorNanos)
    }

    @Test
    fun `same media metadata preserves progress`() {
        var nowNanos = 20_000_000_000L
        val state = playingState(nowNanos) { nowNanos }
        state.applyMetadata(1L, metadata(title = "Same"))
        state.updateProgress(1L, RaopProgress(elapsedMillis = 30_000L, durationMillis = 180_000L))
        val originalAnchor = state.snapshot().progressAnchorNanos

        nowNanos += 2_000_000_000L
        state.applyMetadata(1L, RaopMetadataUpdate(title = "Same"))

        val snapshot = state.snapshot()
        assertEquals(30_000L, snapshot.elapsedMillis)
        assertEquals(32_000L, snapshot.currentElapsedMillis(nowNanos))
        assertEquals(originalAnchor, snapshot.progressAnchorNanos)
    }

    @Test
    fun `sender progress received after media change overrides the reset`() {
        var nowNanos = 30_000_000_000L
        val state = playingState(nowNanos) { nowNanos }
        state.applyMetadata(1L, metadata(title = "First"))
        state.updateProgress(1L, RaopProgress(elapsedMillis = 80_000L, durationMillis = 180_000L))
        state.applyMetadata(1L, metadata(title = "Second"))

        nowNanos += 100_000_000L
        state.updateProgress(1L, RaopProgress(elapsedMillis = 12_345L, durationMillis = 200_000L))

        val snapshot = state.snapshot()
        assertEquals(12_345L, snapshot.elapsedMillis)
        assertEquals(200_000L, snapshot.durationMillis)
        assertEquals(nowNanos, snapshot.progressAnchorNanos)
    }

    @Test
    fun `partial metadata additions do not falsely reset progress`() {
        var nowNanos = 40_000_000_000L
        val state = playingState(nowNanos) { nowNanos }
        state.applyMetadata(1L, RaopMetadataUpdate(title = "Track"))
        state.updateProgress(1L, RaopProgress(elapsedMillis = 25_000L, durationMillis = 180_000L))
        val originalAnchor = state.snapshot().progressAnchorNanos

        nowNanos += 1_000_000_000L
        state.applyMetadata(1L, RaopMetadataUpdate(artist = "Artist"))

        val snapshot = state.snapshot()
        assertEquals("Track", snapshot.title)
        assertEquals("Artist", snapshot.artist)
        assertEquals(25_000L, snapshot.elapsedMillis)
        assertEquals(26_000L, snapshot.currentElapsedMillis(nowNanos))
        assertEquals(originalAnchor, snapshot.progressAnchorNanos)
    }

    @Test
    fun `duration-only metadata update does not reset progress`() {
        var nowNanos = 50_000_000_000L
        val state = playingState(nowNanos) { nowNanos }
        state.applyMetadata(1L, metadata(title = "Track", durationMillis = 180_000L))
        state.updateProgress(1L, RaopProgress(elapsedMillis = 60_000L, durationMillis = 180_000L))
        val originalAnchor = state.snapshot().progressAnchorNanos

        nowNanos += 1_000_000_000L
        state.applyMetadata(1L, RaopMetadataUpdate(durationMillis = 181_000L))

        val snapshot = state.snapshot()
        assertEquals(60_000L, snapshot.elapsedMillis)
        assertEquals(61_000L, snapshot.currentElapsedMillis(nowNanos))
        assertEquals(181_000L, snapshot.durationMillis)
        assertEquals(originalAnchor, snapshot.progressAnchorNanos)
    }

    @Test
    fun `iPhone progress path keeps sender elapsed and duration`() {
        var nowNanos = 60_000_000_000L
        val state = playingState(nowNanos) { nowNanos }

        state.updateProgress(1L, RaopProgress(elapsedMillis = 235_577L, durationMillis = 340_420L))

        var snapshot = state.snapshot()
        assertEquals(235_577L, snapshot.elapsedMillis)
        assertEquals(340_420L, snapshot.durationMillis)
        assertEquals(nowNanos, snapshot.progressAnchorNanos)

        nowNanos += 1_000_000_000L
        snapshot = state.snapshot()
        assertEquals(236_577L, snapshot.currentElapsedMillis(nowNanos))
    }

    @Test
    fun receiverFailureIsVisibleToTheUiState() {
        val state = WindowsNowPlayingState()
        state.receiverFailed(IllegalStateException("TCP 5000 is already in use"))

        assertEquals(WindowsReceiverStatus.ERROR, state.snapshot().status)
        assertEquals("TCP 5000 is already in use", state.snapshot().errorMessage)
    }

    private fun playingState(
        initialNowNanos: Long,
        nanoTime: () -> Long
    ): WindowsNowPlayingState = WindowsNowPlayingState(nanoTime).also { state ->
        state.receiverReady(InetAddress.getByName("192.168.1.5"))
        state.connectionOpened(1L)
        state.playbackStarted(1L)
        assertEquals(initialNowNanos, state.snapshot().progressAnchorNanos)
    }

    private fun metadata(
        title: String,
        durationMillis: Long? = null
    ): RaopMetadataUpdate = RaopMetadataUpdate(
        title = title,
        artist = "Artist",
        album = "Album",
        durationMillis = durationMillis,
        playbackState = RaopPlaybackState.PLAYING
    )
}
