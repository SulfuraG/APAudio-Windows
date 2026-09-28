package com.example.apaudio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsIconExporterTest {
    @Test
    fun generatedIcoContainsSevenPngBackedSizes() {
        val output = Files.createTempFile("apaudio-icon", ".ico")
        try {
            WindowsIconExporter.writeIco(output)
            val bytes = Files.readAllBytes(output)
            val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

            assertEquals(0, header.getShort(0).toInt())
            assertEquals(1, header.getShort(2).toInt())
            assertEquals(7, header.getShort(4).toInt())

            repeat(7) { index ->
                val entryOffset = 6 + index * 16
                val imageLength = header.getInt(entryOffset + 8)
                val imageOffset = header.getInt(entryOffset + 12)
                assertTrue(imageLength > 0)
                assertContentEquals(
                    byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47),
                    bytes.copyOfRange(imageOffset, imageOffset + 4)
                )
            }
        } finally {
            Files.deleteIfExists(output)
        }
    }
}
