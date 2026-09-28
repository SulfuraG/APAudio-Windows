package com.example.apaudio

import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage

internal object WindowsAppIcon {
    fun create(size: Int): BufferedImage {
        require(size > 0) { "Icon size must be positive" }
        return BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB).also { image ->
            val graphics = image.createGraphics()
            try {
                graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                )
                graphics.color = Color(20, 30, 45)
                graphics.fillRoundRect(0, 0, size, size, size / 3, size / 3)
                graphics.color = Color(52, 177, 255)
                val inset = maxOf(1, size / 10)
                graphics.fillOval(inset, inset, size - inset * 2, size - inset * 2)

                graphics.color = Color.WHITE
                graphics.stroke = BasicStroke(
                    maxOf(2f, size / 12f),
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND
                )
                val stemX = (size * 0.61).toInt()
                graphics.drawLine(stemX, (size * 0.28).toInt(), stemX, (size * 0.68).toInt())
                graphics.drawLine((size * 0.40).toInt(), (size * 0.36).toInt(), stemX, (size * 0.28).toInt())
                graphics.fillOval(
                    (size * 0.31).toInt(),
                    (size * 0.58).toInt(),
                    maxOf(3, (size * 0.25).toInt()),
                    maxOf(3, (size * 0.20).toInt())
                )
            } finally {
                graphics.dispose()
            }
        }
    }
}
