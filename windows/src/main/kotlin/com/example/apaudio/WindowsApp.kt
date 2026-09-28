package com.example.apaudio

import java.awt.EventQueue
import java.awt.SystemTray
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

fun main() {
    WindowsAppLog.install()
    WindowsApp().start()
}

/** Owns desktop UI, tray and receiver lifecycle without moving RAOP work onto the EDT. */
internal class WindowsApp {
    private val state = WindowsNowPlayingState()
    private val receiver = WindowsReceiver(state)
    private val uiStarted = AtomicBoolean()
    private val exiting = AtomicBoolean()

    private var mainWindow: WindowsMainWindow? = null
    private var tray: WindowsTray? = null
    private var stateSubscription: Closeable? = null

    private val receiverThread = Thread({ receiver.run() }, "apaudio-windows-receiver").apply {
        isDaemon = false
    }

    fun start() {
        Runtime.getRuntime().addShutdownHook(
            Thread({
                receiver.stop()
                WindowsAppLog.close()
            }, "apaudio-windows-shutdown")
        )
        stateSubscription = state.addListener { snapshot ->
            if (snapshot.status != WindowsReceiverStatus.STARTING) ensureUiStarted()
        }
        receiverThread.start()
    }

    private fun ensureUiStarted() {
        if (!uiStarted.compareAndSet(false, true)) return
        EventQueue.invokeLater {
            val window = WindowsMainWindow(state)
            mainWindow = window

            if (SystemTray.isSupported()) {
                runCatching {
                    WindowsTray(
                        state = state,
                        onOpen = { EventQueue.invokeLater { window.showWindow() } },
                        onExit = ::requestExit
                    )
                }.onSuccess { installedTray ->
                    tray = installedTray
                    window.setTrayAvailable(true)
                }.onFailure { error ->
                    System.err.println("System Tray initialization failed: ${error.message}")
                    JOptionPane.showMessageDialog(
                        null,
                        "System Trayを初期化できませんでした。\n${error.message}",
                        "APAudio Windows",
                        JOptionPane.WARNING_MESSAGE
                    )
                }
            }
            window.showWindow()
        }
    }

    private fun requestExit() {
        if (!exiting.compareAndSet(false, true)) return
        println("APAudio Tray Exit requested")
        Thread({
            receiver.stop()
            if (Thread.currentThread() != receiverThread) {
                runCatching { receiverThread.join(RECEIVER_STOP_TIMEOUT_MILLIS) }
            }
            SwingUtilities.invokeAndWait {
                tray?.close()
                tray = null
                mainWindow?.close()
                mainWindow = null
            }
            stateSubscription?.close()
            stateSubscription = null
            WindowsAppLog.close()
            System.exit(0)
        }, "apaudio-windows-exit").start()
    }

    private companion object {
        const val RECEIVER_STOP_TIMEOUT_MILLIS = 5_000L
    }
}
