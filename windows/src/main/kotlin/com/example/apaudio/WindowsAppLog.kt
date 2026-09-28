package com.example.apaudio

import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant

/** Keeps existing console diagnostics available when jpackage launches without a console window. */
internal object WindowsAppLog {
    private var installed = false
    private var previousOut: PrintStream? = null
    private var previousErr: PrintStream? = null
    private var fileOutput: OutputStream? = null

    @Synchronized
    fun install() {
        if (installed) return
        val originalOut = System.out
        val originalErr = System.err
        runCatching {
            val path = logPath()
            Files.createDirectories(path.parent)
            val synchronizedFile = SynchronizedOutputStream(
                Files.newOutputStream(
                    path,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
                )
            )
            previousOut = originalOut
            previousErr = originalErr
            fileOutput = synchronizedFile
            System.setOut(
                PrintStream(TeeOutputStream(originalOut, synchronizedFile), true, StandardCharsets.UTF_8)
            )
            System.setErr(
                PrintStream(TeeOutputStream(originalErr, synchronizedFile), true, StandardCharsets.UTF_8)
            )
            installed = true
            println("===== APAudio start ${Instant.now()} =====")
            println("APAudio log file: ${path.toAbsolutePath()}")
        }.onFailure { error ->
            originalErr.println("APAudio file logging unavailable: ${error.message}")
        }
    }

    @Synchronized
    fun close() {
        if (!installed) return
        println("===== APAudio stop ${Instant.now()} =====")
        System.out.flush()
        System.err.flush()
        previousOut?.let(System::setOut)
        previousErr?.let(System::setErr)
        runCatching { fileOutput?.close() }
        previousOut = null
        previousErr = null
        fileOutput = null
        installed = false
    }

    private fun logPath(): Path {
        val base = System.getenv("LOCALAPPDATA")
            ?.takeIf { it.isNotBlank() }
            ?.let(Path::of)
            ?: Path.of(System.getProperty("user.home"), "AppData", "Local")
        return base.resolve("APAudio").resolve("logs").resolve("APAudio.log")
    }

    private class TeeOutputStream(
        private val first: OutputStream,
        private val second: OutputStream
    ) : OutputStream() {
        override fun write(value: Int) {
            first.write(value)
            second.write(value)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            first.write(bytes, offset, length)
            second.write(bytes, offset, length)
        }

        override fun flush() {
            first.flush()
            second.flush()
        }
    }

    private class SynchronizedOutputStream(
        private val delegate: OutputStream
    ) : OutputStream() {
        @Synchronized
        override fun write(value: Int) = delegate.write(value)

        @Synchronized
        override fun write(bytes: ByteArray, offset: Int, length: Int) =
            delegate.write(bytes, offset, length)

        @Synchronized
        override fun flush() = delegate.flush()

        @Synchronized
        override fun close() = delegate.close()
    }
}
