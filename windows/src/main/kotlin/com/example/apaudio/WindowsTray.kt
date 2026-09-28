package com.example.apaudio

import java.awt.EventQueue
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.io.Closeable

internal class WindowsTray(
    state: WindowsNowPlayingState,
    onOpen: () -> Unit,
    onExit: () -> Unit
) : Closeable {
    private val systemTray = SystemTray.getSystemTray()
    private val statusItem = MenuItem()
    private val trayIcon: TrayIcon
    private val subscription: Closeable

    init {
        check(EventQueue.isDispatchThread()) { "WindowsTray must be created on the EDT" }
        check(SystemTray.isSupported()) { "System Tray is not supported on this desktop" }

        val menu = PopupMenu()
        menu.add(MenuItem("APAudioを開く").apply { addActionListener { onOpen() } })
        statusItem.isEnabled = false
        menu.add(statusItem)
        menu.addSeparator()
        menu.add(MenuItem("終了").apply { addActionListener { onExit() } })

        trayIcon = TrayIcon(WindowsAppIcon.create(32), "APAudio Windows", menu).apply {
            isImageAutoSize = true
            addActionListener { onOpen() }
        }
        systemTray.add(trayIcon)
        println("APAudio System Tray icon registered")

        subscription = state.addListener { snapshot ->
            EventQueue.invokeLater {
                statusItem.label = "状態: ${snapshot.status.displayText}"
                trayIcon.toolTip = "APAudio Windows — ${snapshot.status.displayText}"
            }
        }
    }

    override fun close() {
        check(EventQueue.isDispatchThread()) { "Tray disposal must run on the EDT" }
        subscription.close()
        systemTray.remove(trayIcon)
        println("APAudio System Tray icon removed")
    }
}
