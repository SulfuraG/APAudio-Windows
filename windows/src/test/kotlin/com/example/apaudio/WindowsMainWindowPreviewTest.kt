package com.example.apaudio

import java.awt.Dimension
import java.awt.Window
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.io.File
import java.net.InetAddress
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Renders only synthetic display state. Never starts a receiver or opens an audio device. */
class WindowsMainWindowPreviewTest {
    @Test fun renderFreeUiAndPreserveCloseToTray() {
        SwingUtilities.invokeAndWait {
            val state = WindowsNowPlayingState()
            val window = WindowsMainWindow(state)
            try {
                window.setTrayAvailable(true)
                window.showWindow()
                val frame = Window.getWindows().filterIsInstance<JFrame>()
                    .single { it.isDisplayable && it.title == "APAudio Windows" }
                state.receiverReady(InetAddress.getByName("192.0.2.10"))
                capture(frame, "waiting")
                state.connectionOpened(1)
                state.playbackStarted(1)
                state.applyMetadata(1, RaopMetadataUpdate(
                    title = "夜明けのプレイリスト",
                    artist = "APAudio Preview",
                    album = "Home listening",
                    durationMillis = 240000,
                    playbackState = RaopPlaybackState.PLAYING
                ))
                state.updateProgress(1, RaopProgress(65000, 240000))
                state.updateVolume(1, -20f)
                capture(frame, "playing")
                frame.size = Dimension(760, 520)
                state.applyMetadata(1, RaopMetadataUpdate(
                    title = "長い曲名の表示確認 — Live Recording / Special Edition",
                    artist = "アーティスト名の表示確認",
                    album = "Album title"
                ))
                capture(frame, "minimum-long-title")
                state.receiverFailed(IllegalStateException("TCP 5000 is already in use"))
                capture(frame, "error")
                frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
                assertFalse(frame.isVisible)
                assertTrue(frame.isDisplayable)
                window.showWindow()
                assertTrue(frame.isVisible)
            } finally {
                window.close()
            }
        }
    }

    private fun capture(frame: JFrame, name: String) {
        frame.validate()
        val image = BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try { frame.paint(graphics) } finally { graphics.dispose() }
        val file = File("build/ui-preview/$name.png")
        file.parentFile.mkdirs()
        ImageIO.write(image, "png", file)
        assertTrue(file.length() > 0)
    }
}
