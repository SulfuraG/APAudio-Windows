package com.example.apaudio

import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt

internal object WindowsArtworkDecoder {
    fun decodeJpeg(body: ByteArray, targetSizePx: Int): BufferedImage? {
        if (body.isEmpty() || targetSizePx <= 0) return null
        val source = ImageIO.read(ByteArrayInputStream(body)) ?: return null
        val largestDimension = maxOf(source.width, source.height)
        if (largestDimension <= targetSizePx) return source

        val scale = targetSizePx.toDouble() / largestDimension.toDouble()
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also { resized ->
            resized.createGraphics().use { graphics ->
                graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
                )
                graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY
                )
                graphics.drawImage(source, 0, 0, width, height, null)
            }
        }
    }

    private inline fun <T : java.awt.Graphics> T.use(block: (T) -> Unit) {
        try {
            block(this)
        } finally {
            dispose()
        }
    }
}
