package com.example.apaudio

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.io.Closeable
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.WindowConstants

/** Swing main window. All rendering is marshalled onto the EDT. */
internal class WindowsMainWindow(
    private val state: WindowsNowPlayingState
) : Closeable {
    private val frame = JFrame("APAudio Windows")
    private val statusLabel = JLabel()
    private val titleLabel = JLabel()
    private val artistLabel = JLabel()
    private val albumLabel = JLabel()
    private val playbackLabel = JLabel()
    private val progressBar = JProgressBar(0, PROGRESS_MAX)
    private val timeLabel = JLabel()
    private val endpointLabel = JLabel()
    private val volumeLabel = JLabel()
    private val helpLabel = JLabel()
    private val artworkPanel = ArtworkPanel()
    private var latestSnapshot = state.snapshot()
    private var trayAvailable = false

    private val subscription = state.addListener { snapshot ->
        if (SwingUtilities.isEventDispatchThread()) {
            render(snapshot)
        } else {
            SwingUtilities.invokeLater { render(snapshot) }
        }
    }
    private val progressTimer = Timer(1_000) { renderProgress(latestSnapshot) }

    init {
        check(SwingUtilities.isEventDispatchThread()) { "WindowsMainWindow must be created on the EDT" }
        configureFrame()
        render(latestSnapshot)
        progressTimer.start()
    }

    fun setTrayAvailable(available: Boolean) {
        trayAvailable = available
    }

    fun showWindow() {
        check(SwingUtilities.isEventDispatchThread()) { "Window visibility must change on the EDT" }
        if (!frame.isVisible) frame.isVisible = true
        frame.extendedState = JFrame.NORMAL
        frame.toFront()
        frame.requestFocus()
        println("APAudio main window shown")
    }

    override fun close() {
        check(SwingUtilities.isEventDispatchThread()) { "Window disposal must run on the EDT" }
        progressTimer.stop()
        subscription.close()
        frame.dispose()
    }

    private fun configureFrame() {
        frame.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
        frame.iconImage = WindowsAppIcon.create(64)
        frame.minimumSize = Dimension(760, 520)
        frame.size = Dimension(860, 540)
        frame.contentPane.background = BACKGROUND
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(event: WindowEvent) {
                if (trayAvailable) {
                    frame.isVisible = false
                    println("APAudio main window hidden to System Tray")
                }
            }
        })

        val root = JPanel(BorderLayout(0, 24)).apply {
            background = BACKGROUND
            border = BorderFactory.createEmptyBorder(24, 28, 22, 28)
        }
        val header = JPanel(BorderLayout()).apply { isOpaque = false }
        header.add(textLabel("APAudio", 22, Color.WHITE, Font.BOLD), BorderLayout.WEST)
        statusLabel.font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
        header.add(statusLabel, BorderLayout.EAST)
        root.add(header, BorderLayout.NORTH)

        val player = JPanel(BorderLayout(28, 0)).apply {
            background = SURFACE
            border = BorderFactory.createEmptyBorder(24, 24, 24, 24)
        }
        artworkPanel.preferredSize = Dimension(240, 240)
        player.add(artworkPanel, BorderLayout.WEST)
        val details = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        details.add(textLabel("NOW PLAYING", 11, SECONDARY_TEXT, Font.BOLD))
        details.add(Box.createVerticalStrut(16))
        configureLabel(titleLabel, 26, Color.WHITE, Font.BOLD)
        details.add(titleLabel)
        details.add(Box.createVerticalStrut(10))
        configureLabel(artistLabel, 16, PRIMARY_TEXT, Font.PLAIN)
        details.add(artistLabel)
        details.add(Box.createVerticalStrut(6))
        configureLabel(albumLabel, 13, SECONDARY_TEXT, Font.PLAIN)
        details.add(albumLabel)
        details.add(Box.createVerticalGlue())
        configureLabel(playbackLabel, 13, ACCENT, Font.PLAIN)
        details.add(playbackLabel)
        details.add(Box.createVerticalStrut(12))
        progressBar.maximumSize = Dimension(Int.MAX_VALUE, 6)
        progressBar.preferredSize = Dimension(300, 6)
        progressBar.foreground = ACCENT
        progressBar.background = Color(48, 55, 65)
        progressBar.border = BorderFactory.createEmptyBorder()
        progressBar.toolTipText = "再生位置の表示です。曲の操作は送信元で行ってください。"
        details.add(progressBar)
        details.add(Box.createVerticalStrut(8))
        configureLabel(timeLabel, 12, SECONDARY_TEXT, Font.PLAIN)
        details.add(timeLabel)
        player.add(details, BorderLayout.CENTER)
        root.add(player, BorderLayout.CENTER)

        val footer = JPanel(BorderLayout(12, 8)).apply { isOpaque = false }
        val receiverInfo = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }
        configureLabel(endpointLabel, 12, PRIMARY_TEXT, Font.PLAIN)
        receiverInfo.add(endpointLabel)
        configureLabel(volumeLabel, 12, SECONDARY_TEXT, Font.PLAIN)
        receiverInfo.add(volumeLabel)
        footer.add(receiverInfo, BorderLayout.CENTER)
        val helpButton = javax.swing.JButton("接続ガイド").apply {
            font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
            isFocusPainted = true
            toolTipText = "AirPlayの接続方法と接続情報を表示"
            addActionListener { showConnectionGuide() }
        }
        footer.add(helpButton, BorderLayout.EAST)
        helpLabel.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
        helpLabel.foreground = SECONDARY_TEXT
        footer.add(helpLabel, BorderLayout.SOUTH)
        root.add(footer, BorderLayout.SOUTH)
        frame.contentPane = root
        frame.setLocationRelativeTo(null)
    }

    private fun showConnectionGuide() {
        val address = latestSnapshot.advertisedAddress ?: "未取得"
        javax.swing.JOptionPane.showMessageDialog(
            frame,
            "1. iPhone・MacとPCを同じ家庭内ネットワークに接続します。\n" +
                "2. 送信元のAirPlay音声出力先で「APAudio Windows」を選びます。\n" +
                "3. 曲の選択や音量調整は送信元で行います。\n\n" +
                "表示されない場合は、ネットワーク接続とWindowsの\n" +
                "プライベートネットワーク用ファイアウォール許可を確認してください。\n\n" +
                "接続情報: " + address + ":5000\n" +
                "音声出力: Windowsの既定の出力（接続開始時）\n" +
                "ログ: %LOCALAPPDATA%\\APAudio\\logs\\APAudio.log",
            "APAudio — 接続ガイド",
            javax.swing.JOptionPane.INFORMATION_MESSAGE
        )
    }

    private fun render(snapshot: WindowsNowPlayingSnapshot) {
        check(SwingUtilities.isEventDispatchThread()) { "Window rendering must run on the EDT" }
        latestSnapshot = snapshot
        statusLabel.text = when (snapshot.status) {
            WindowsReceiverStatus.STARTING -> "準備中"
            WindowsReceiverStatus.WAITING -> "接続待機中"
            WindowsReceiverStatus.CONNECTED -> "接続中"
            WindowsReceiverStatus.PLAYING -> "再生中"
            WindowsReceiverStatus.ERROR -> "接続を確認してください"
            WindowsReceiverStatus.STOPPED -> "受信停止中"
        }
        statusLabel.foreground = if (snapshot.status == WindowsReceiverStatus.ERROR) ERROR else ACCENT

        val fallback = fallbackText(snapshot)
        titleLabel.text = html(snapshot.title ?: fallback.first)
        artistLabel.text = html(snapshot.artist ?: fallback.second)
        albumLabel.text = html(snapshot.album.orEmpty())
        playbackLabel.text = when (snapshot.playbackState) {
            RaopPlaybackState.PLAYING -> "▶  再生中"
            RaopPlaybackState.PAUSED -> "Ⅱ  一時停止中"
            null -> when (snapshot.status) {
                WindowsReceiverStatus.CONNECTED -> "AirPlay接続済み"
                WindowsReceiverStatus.PLAYING -> "▶  再生中"
                else -> ""
            }
        }
        endpointLabel.text = snapshot.advertisedAddress?.let { address ->
            "受信名  APAudio Windows"
        } ?: "受信名  APAudio Windows"
        endpointLabel.toolTipText = snapshot.advertisedAddress?.let { "接続情報: $it:5000" }
        helpLabel.text = if (trayAvailable) "ウィンドウを閉じても受信は続きます。終了はトレイから。" else "曲の選択・再生操作はiPhoneやMacから行えます。"
        volumeLabel.text = snapshot.volumeDb?.let { volumeDb ->
            if (volumeDb <= -144f) "送信元音量  ミュート" else "送信元音量  %.1f dB".format(volumeDb)
        } ?: ""
        artworkPanel.setArtwork(snapshot.artwork)
        renderProgress(snapshot)
    }

    private fun fallbackText(snapshot: WindowsNowPlayingSnapshot): Pair<String, String> =
        when (snapshot.status) {
            WindowsReceiverStatus.STARTING ->
                "APAudio Windows" to "受信の準備をしています"
            WindowsReceiverStatus.WAITING ->
                "AirPlay接続待機中" to "iPhone・MacのAirPlayから接続できます"
            WindowsReceiverStatus.CONNECTED ->
                "接続中" to "音楽情報が届くとここに表示されます"
            WindowsReceiverStatus.PLAYING ->
                "再生中" to "音声を受信しています"
            WindowsReceiverStatus.ERROR ->
                "受信を開始できませんでした" to (snapshot.errorMessage ?: "接続ガイドから確認してください")
            WindowsReceiverStatus.STOPPED ->
                "受信停止中" to "APAudio Windowsは停止しています"
        }

    private fun renderProgress(snapshot: WindowsNowPlayingSnapshot) {
        val elapsed = snapshot.currentElapsedMillis()
        progressBar.value = if (snapshot.durationMillis > 0L) {
            ((elapsed * PROGRESS_MAX) / snapshot.durationMillis)
                .toInt()
                .coerceIn(0, PROGRESS_MAX)
        } else {
            0
        }
        timeLabel.text = "${formatTime(elapsed)} / ${formatTime(snapshot.durationMillis)}"
    }

    private fun configureLabel(label: JLabel, size: Int, color: Color, style: Int) {
        label.font = Font(Font.SANS_SERIF, style, size)
        label.foreground = color
        label.alignmentX = 0f
        label.maximumSize = Dimension(Int.MAX_VALUE, 70)
    }

    private fun textLabel(text: String, size: Int, color: Color, style: Int): JLabel =
        JLabel(text).apply { configureLabel(this, size, color, style) }

    private fun html(text: String): String =
        "<html>${text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</html>"

    private fun formatTime(valueMillis: Long): String {
        val totalSeconds = valueMillis.coerceAtLeast(0L) / 1_000L
        return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
    }

    private class ArtworkPanel : JPanel() {
        private var artwork: BufferedImage? = null

        init {
            background = ARTWORK_BACKGROUND
            minimumSize = Dimension(260, 260)
        }

        fun setArtwork(image: BufferedImage?) {
            artwork = image
            repaint()
        }

        override fun paintComponent(graphics: Graphics) {
            super.paintComponent(graphics)
            val image = artwork
            if (image == null) {
                val g2 = graphics.create() as Graphics2D
                try {
                    g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                    )
                    g2.color = ACCENT
                    g2.font = Font(Font.SANS_SERIF, Font.BOLD, minOf(width, height) / 3)
                    val note = "♪"
                    val metrics = g2.fontMetrics
                    g2.drawString(
                        note,
                        (width - metrics.stringWidth(note)) / 2,
                        (height - metrics.height) / 2 + metrics.ascent
                    )
                } finally {
                    g2.dispose()
                }
                return
            }

            val scale = minOf(width.toDouble() / image.width, height.toDouble() / image.height)
            val drawWidth = (image.width * scale).toInt()
            val drawHeight = (image.height * scale).toInt()
            val x = (width - drawWidth) / 2
            val y = (height - drawHeight) / 2
            val g2 = graphics.create() as Graphics2D
            try {
                g2.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
                )
                g2.drawImage(image, x, y, drawWidth, drawHeight, null)
            } finally {
                g2.dispose()
            }
        }
    }

    private companion object {
        const val PROGRESS_MAX = 1_000
        val BACKGROUND = Color(17, 20, 25)
        val SURFACE = Color(26, 30, 37)
        val ARTWORK_BACKGROUND = Color(35, 41, 50)
        val ACCENT = Color(111, 183, 245)
        val PRIMARY_TEXT = Color(216, 225, 235)
        val SECONDARY_TEXT = Color(145, 160, 178)
        val ERROR = Color(255, 112, 112)
    }
}
