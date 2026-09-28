package com.example.apaudio

import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WindowsReceiverStartupTest {
    @Test
    fun tcpBindFailureIsPublishedForTheDesktopUi() {
        ServerSocket().use { occupied ->
            occupied.bind(InetSocketAddress("127.0.0.1", 0))
            val state = WindowsNowPlayingState()
            val receiver = WindowsReceiver(state, occupied.localPort)

            receiver.run()

            assertEquals(WindowsReceiverStatus.ERROR, state.snapshot().status)
            assertNotNull(state.snapshot().errorMessage)
        }
    }
}
