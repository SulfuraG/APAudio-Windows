package com.example.apaudio

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

fun main(arguments: Array<String>) {
    require(arguments.size == 1) { "Expected the output ICO path" }
    WindowsIconExporter.writeIco(Path.of(arguments.single()))
}

/** Writes a PNG-backed multi-resolution ICO using the same renderer as Swing and System Tray. */
internal object WindowsIconExporter {
    private val iconSizes = intArrayOf(16, 24, 32, 48, 64, 128, 256)

    fun writeIco(output: Path) {
        val images = iconSizes.map { size ->
            ByteArrayOutputStream().use { bytes ->
                check(ImageIO.write(WindowsAppIcon.create(size), "png", bytes)) {
                    "PNG ImageIO writer is unavailable"
                }
                IconImage(size, bytes.toByteArray())
            }
        }

        output.parent?.let(Files::createDirectories)
        DataOutputStream(Files.newOutputStream(output)).use { stream ->
            stream.writeLittleEndianShort(0)
            stream.writeLittleEndianShort(1)
            stream.writeLittleEndianShort(images.size)

            var imageOffset = ICO_HEADER_BYTES + images.size * ICO_DIRECTORY_ENTRY_BYTES
            images.forEach { image ->
                stream.write(if (image.size == 256) 0 else image.size)
                stream.write(if (image.size == 256) 0 else image.size)
                stream.write(0)
                stream.write(0)
                stream.writeLittleEndianShort(1)
                stream.writeLittleEndianShort(32)
                stream.writeLittleEndianInt(image.png.size)
                stream.writeLittleEndianInt(imageOffset)
                imageOffset += image.png.size
            }
            images.forEach { image -> stream.write(image.png) }
        }
    }

    private fun DataOutputStream.writeLittleEndianShort(value: Int) {
        writeByte(value and 0xff)
        writeByte((value ushr 8) and 0xff)
    }

    private fun DataOutputStream.writeLittleEndianInt(value: Int) {
        writeByte(value and 0xff)
        writeByte((value ushr 8) and 0xff)
        writeByte((value ushr 16) and 0xff)
        writeByte((value ushr 24) and 0xff)
    }

    private data class IconImage(val size: Int, val png: ByteArray)

    private const val ICO_HEADER_BYTES = 6
    private const val ICO_DIRECTORY_ENTRY_BYTES = 16
}
