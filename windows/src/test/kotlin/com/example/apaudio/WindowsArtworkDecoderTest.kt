package com.example.apaudio

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WindowsArtworkDecoderTest {
    @Test
    fun jpegIsDecodedAndBoundedOffTheUiLayer() {
        val source = BufferedImage(128, 64, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().also { graphics ->
                graphics.color = Color(30, 120, 220)
                graphics.fillRect(0, 0, width, height)
                graphics.dispose()
            }
        }
        val body = ByteArrayOutputStream().also { output ->
            ImageIO.write(source, "jpeg", output)
        }.toByteArray()

        val decoded = requireNotNull(WindowsArtworkDecoder.decodeJpeg(body, 32))

        assertEquals(32, decoded.width)
        assertEquals(16, decoded.height)
    }

    @Test
    fun invalidArtworkDoesNotProduceAnImage() {
        assertNull(WindowsArtworkDecoder.decodeJpeg(byteArrayOf(1, 2, 3), 64))
        assertNull(WindowsArtworkDecoder.decodeJpeg(ByteArray(0), 64))
    }
}
