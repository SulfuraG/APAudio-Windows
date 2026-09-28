package com.example.apaudio

import java.awt.image.BufferedImage
import java.io.Closeable
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList

internal enum class WindowsReceiverStatus(val displayText: String) {
    STARTING("Receiver起動中"),
    WAITING("AirPlay接続待機中"),
    CONNECTED("接続中"),
    PLAYING("再生中"),
    ERROR("Receiver起動エラー"),
    STOPPED("Receiver停止中")
}

internal data class WindowsNowPlayingSnapshot(
    val status: WindowsReceiverStatus = WindowsReceiverStatus.STARTING,
    val advertisedAddress: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artwork: BufferedImage? = null,
    val playbackState: RaopPlaybackState? = null,
    val elapsedMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val progressAnchorNanos: Long = 0L,
    val volumeDb: Float? = null,
    val errorMessage: String? = null
) {
    fun currentElapsedMillis(nowNanos: Long = System.nanoTime()): Long {
        val estimated = if (playbackState == RaopPlaybackState.PLAYING) {
            elapsedMillis + ((nowNanos - progressAnchorNanos).coerceAtLeast(0L) / 1_000_000L)
        } else {
            elapsedMillis
        }
        return if (durationMillis > 0L) {
            estimated.coerceIn(0L, durationMillis)
        } else {
            estimated.coerceAtLeast(0L)
        }
    }
}

/** Thread-safe bridge between the RAOP receiver and desktop UI. */
internal class WindowsNowPlayingState(
    private val nanoTime: () -> Long = System::nanoTime
) {
    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<(WindowsNowPlayingSnapshot) -> Unit>()

    @Volatile
    private var current = WindowsNowPlayingSnapshot(progressAnchorNanos = nanoTime())
    private var currentConnectionId: Long? = null

    fun snapshot(): WindowsNowPlayingSnapshot = current

    fun addListener(listener: (WindowsNowPlayingSnapshot) -> Unit): Closeable {
        listeners += listener
        listener(snapshot())
        return Closeable { listeners -= listener }
    }

    fun receiverReady(address: InetAddress) {
        val updated = synchronized(lock) {
            currentConnectionId = null
            WindowsNowPlayingSnapshot(
                status = WindowsReceiverStatus.WAITING,
                advertisedAddress = address.hostAddress,
                progressAnchorNanos = nanoTime()
            ).also { current = it }
        }
        publish(updated)
    }

    fun connectionOpened(connectionId: Long) {
        val updated = synchronized(lock) {
            currentConnectionId = connectionId
            WindowsNowPlayingSnapshot(
                status = WindowsReceiverStatus.CONNECTED,
                advertisedAddress = current.advertisedAddress,
                progressAnchorNanos = nanoTime()
            ).also { current = it }
        }
        publish(updated)
    }

    fun playbackStarted(connectionId: Long) {
        updateForConnection(connectionId) { snapshot ->
            snapshot.copy(
                status = WindowsReceiverStatus.PLAYING,
                playbackState = RaopPlaybackState.PLAYING,
                progressAnchorNanos = nanoTime()
            )
        }
    }

    fun applyMetadata(connectionId: Long, metadata: RaopMetadataUpdate) {
        updateForConnection(connectionId) { snapshot ->
            val mediaIdentityChanged = hasMediaIdentityChanged(snapshot, metadata)
            val playbackState = metadata.playbackState ?: snapshot.playbackState
            val stateChangedAt = nanoTime()
            val elapsedMillis = when {
                mediaIdentityChanged -> 0L
                metadata.playbackState != null &&
                    snapshot.playbackState == RaopPlaybackState.PLAYING ->
                    snapshot.currentElapsedMillis(stateChangedAt)
                else -> snapshot.elapsedMillis
            }
            snapshot.copy(
                status = when (playbackState) {
                    RaopPlaybackState.PLAYING -> WindowsReceiverStatus.PLAYING
                    RaopPlaybackState.PAUSED -> WindowsReceiverStatus.CONNECTED
                    null -> snapshot.status
                },
                title = metadata.title ?: snapshot.title,
                artist = metadata.artist ?: snapshot.artist,
                album = metadata.album ?: snapshot.album,
                durationMillis = when {
                    mediaIdentityChanged -> metadata.durationMillis?.coerceAtLeast(0L) ?: 0L
                    else -> metadata.durationMillis?.coerceAtLeast(0L) ?: snapshot.durationMillis
                },
                playbackState = playbackState,
                elapsedMillis = elapsedMillis,
                progressAnchorNanos = if (mediaIdentityChanged || metadata.playbackState != null) {
                    stateChangedAt
                } else {
                    snapshot.progressAnchorNanos
                }
            )
        }
    }

    fun updateProgress(connectionId: Long, progress: RaopProgress) {
        updateForConnection(connectionId) { snapshot ->
            snapshot.copy(
                elapsedMillis = progress.elapsedMillis.coerceAtLeast(0L),
                durationMillis = progress.durationMillis.coerceAtLeast(0L),
                progressAnchorNanos = nanoTime()
            )
        }
    }

    fun updateVolume(connectionId: Long, volumeDb: Float) {
        if (!volumeDb.isFinite()) return
        updateForConnection(connectionId) { snapshot -> snapshot.copy(volumeDb = volumeDb) }
    }

    fun updateArtwork(connectionId: Long, artwork: BufferedImage?) {
        updateForConnection(connectionId) { snapshot -> snapshot.copy(artwork = artwork) }
    }

    fun connectionClosed(connectionId: Long) {
        val updated = synchronized(lock) {
            if (currentConnectionId != connectionId) return
            currentConnectionId = null
            WindowsNowPlayingSnapshot(
                status = WindowsReceiverStatus.WAITING,
                advertisedAddress = current.advertisedAddress,
                progressAnchorNanos = nanoTime()
            ).also { current = it }
        }
        publish(updated)
    }

    fun receiverFailed(error: Throwable) {
        val message = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        val updated = synchronized(lock) {
            currentConnectionId = null
            WindowsNowPlayingSnapshot(
                status = WindowsReceiverStatus.ERROR,
                advertisedAddress = current.advertisedAddress,
                progressAnchorNanos = nanoTime(),
                errorMessage = message
            ).also { current = it }
        }
        publish(updated)
    }

    fun receiverStopped() {
        val updated = synchronized(lock) {
            currentConnectionId = null
            WindowsNowPlayingSnapshot(
                status = WindowsReceiverStatus.STOPPED,
                advertisedAddress = current.advertisedAddress,
                progressAnchorNanos = nanoTime()
            ).also { current = it }
        }
        publish(updated)
    }

    private fun updateForConnection(
        connectionId: Long,
        transform: (WindowsNowPlayingSnapshot) -> WindowsNowPlayingSnapshot
    ) {
        val updated = synchronized(lock) {
            if (currentConnectionId != connectionId) return
            transform(current).also { current = it }
        }
        publish(updated)
    }

    private fun publish(snapshot: WindowsNowPlayingSnapshot) {
        listeners.forEach { listener ->
            runCatching { listener(snapshot) }
                .onFailure { error ->
                    System.err.println("Windows UI state listener failed: ${error.message}")
                }
        }
    }

    private fun hasMediaIdentityChanged(
        snapshot: WindowsNowPlayingSnapshot,
        metadata: RaopMetadataUpdate
    ): Boolean =
        fieldChanged(snapshot.title, metadata.title) ||
            fieldChanged(snapshot.artist, metadata.artist) ||
            fieldChanged(snapshot.album, metadata.album)

    private fun fieldChanged(existing: String?, received: String?): Boolean =
        existing != null && received != null && existing != received
}
