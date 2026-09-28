package com.example.apaudio

import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private const val RTSP_PORT = 5000
private const val DEVICE_NAME = "APAudio Windows"
private const val ARTWORK_TARGET_SIZE_PX = 640

internal class WindowsReceiver(
    private val nowPlayingState: WindowsNowPlayingState = WindowsNowPlayingState(),
    private val rtspPort: Int = RTSP_PORT
) {
    private val connectionCounter = AtomicLong()
    private val connectionSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val connectionExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "raop-windows-client").apply { isDaemon = true }
    }
    private val metadataExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "raop-windows-metadata").apply { isDaemon = true }
    }
    private val volumeStore = WindowsRaopVolumeStore()

    @Volatile
    private var running = true
    private var serverSocket: ServerSocket? = null
    private var advertiser: WindowsRaopAdvertiser? = null

    fun run() {
        try {
            val listener = ServerSocket()
            serverSocket = listener
            listener.reuseAddress = true
            listener.bind(InetSocketAddress(rtspPort))

            val mdns = WindowsRaopAdvertiser(DEVICE_NAME, rtspPort)
            advertiser = mdns
            val advertisedAddress = mdns.start()

            nowPlayingState.receiverReady(advertisedAddress)

            println("APAudio Windows Phase 3")
            println("RTSP listening on port $rtspPort")
            println("RAOP advertised from ${advertisedAddress.hostAddress} as '$DEVICE_NAME'")
            println("Select '$DEVICE_NAME' from the AirPlay audio output list.")

            while (running && !listener.isClosed) {
                val client = listener.accept()
                connectionSockets += client
                val connectionId = connectionCounter.incrementAndGet()
                println("RAOP connection #$connectionId from ${client.inetAddress.hostAddress}")
                connectionExecutor.execute { handleConnection(client, connectionId) }
            }
        } catch (error: Exception) {
            if (running) {
                nowPlayingState.receiverFailed(error)
                System.err.println("APAudio Windows receiver failed: ${error.message}")
                error.printStackTrace(System.err)
            }
        } finally {
            closeResources()
        }
    }

    private fun handleConnection(client: Socket, connectionId: Long) {
        var udpPorts: RaopUdpPorts? = null
        var audioSession: RaopAudioSession? = null
        var audioOutput: WindowsRaopAudioOutput? = null
        var endReason = "eof"
        var senderShapeLogged = false
        val observedSetParameterShapes = linkedSetOf<String>()

        try {
            client.use { socket ->
                val input = RtspRequestReader(socket.getInputStream().buffered())
                val output = socket.getOutputStream()

                while (running) {
                    val request = input.read() ?: break
                    val cseq = request.header("CSeq") ?: "0"
                    println("#$connectionId >>> ${request.requestLine}")

                    if (!senderShapeLogged && hasSenderShape(request)) {
                        println("#$connectionId ${WindowsSenderDiagnostics.describeSender(request)}")
                        senderShapeLogged = true
                    }

                    when (request.method) {
                        "OPTIONS" -> {
                            val appleResponseHeader = request.header("Apple-Challenge")?.let { challenge ->
                                runCatching {
                                    val response = AppleChallengeResponse.create(
                                        challenge = Base64.getDecoder().decode(challenge),
                                        localAddress = socket.localAddress.address,
                                        deviceIdHex = WindowsRaopAdvertiser.DEVICE_ID
                                    )
                                    val encoded = Base64.getEncoder()
                                        .withoutPadding()
                                        .encodeToString(response)
                                    "Apple-Response: $encoded\r\n"
                                }.onFailure { error ->
                                    System.err.println("Apple-Response generation failed: ${error.message}")
                                }.getOrDefault("")
                            } ?: ""

                            sendResponse(
                                output,
                                cseq,
                                extraHeaders = buildString {
                                    append("Public: ANNOUNCE, SETUP, RECORD, PAUSE, FLUSH, TEARDOWN, OPTIONS, GET_PARAMETER, SET_PARAMETER\r\n")
                                    append(appleResponseHeader)
                                }
                            )
                        }

                        "ANNOUNCE" -> {
                            runCatching {
                                val sdp = RaopSdpParser.parse(request.body.toString(Charsets.UTF_8))
                                val aesKey = AppleChallengeResponse.decryptAesKey(
                                    Base64.getDecoder().decode(sdp.encryptedAesKeyBase64)
                                )
                                val aesIv = Base64.getDecoder().decode(sdp.aesIvBase64)
                                audioSession = RaopAudioSession(
                                    codec = sdp.codec,
                                    format = sdp.format,
                                    aesKey = aesKey,
                                    aesIv = aesIv
                                )
                                println(
                                    "#$connectionId ANNOUNCE: ${sdp.codec}, " +
                                        "${sdp.format.sampleRate}Hz, ${sdp.format.bitDepth}-bit, " +
                                        "${sdp.format.channels}ch, frame=${sdp.format.frameLength}"
                                )
                            }.onSuccess {
                                nowPlayingState.connectionOpened(connectionId)
                                sendResponse(output, cseq)
                            }.onFailure { error ->
                                System.err.println("ANNOUNCE failed: ${error.message}")
                                sendResponse(output, cseq, statusCode = 400, reason = "Bad Request")
                            }
                        }

                        "SETUP" -> {
                            val transportHeader = request.header("Transport")
                            if (transportHeader == null) {
                                sendResponse(
                                    output,
                                    cseq,
                                    statusCode = 451,
                                    reason = "Parameter Not Understood"
                                )
                                continue
                            }

                            runCatching {
                                val clientTransport = RaopTransport.parseClient(transportHeader)
                                val session = requireNotNull(audioSession) {
                                    "SETUP received before a valid ANNOUNCE"
                                }
                                val allocatedPorts = RaopUdpPorts.open(socket.localAddress)
                                udpPorts?.close()
                                udpPorts = allocatedPorts

                                val decryptor = RaopAudioDecryptor(session)
                                val alacDecoder = RaopAlacDecoder(session.format)
                                val newAudioOutput = WindowsRaopAudioOutput(
                                    sampleRate = session.format.sampleRate,
                                    channels = session.format.channels,
                                    frameLength = session.format.frameLength
                                )
                                val rememberedVolume = volumeStore.load()
                                newAudioOutput.setVolumeDb(rememberedVolume)
                                nowPlayingState.updateVolume(connectionId, rememberedVolume)
                                audioOutput?.close()
                                audioOutput = newAudioOutput

                                var packetCount = 0L
                                allocatedPorts.startDiagnostics(
                                    audioPacketObserver = audio@{ packet ->
                                        val decrypted = runCatching { decryptor.decrypt(packet) }
                                            .onFailure { error ->
                                                System.err.println("RTP/AES decrypt failed: ${error.message}")
                                            }
                                            .getOrNull() ?: return@audio
                                        val pcm = runCatching {
                                            alacDecoder.decode(decrypted.decryptedPayload)
                                        }.onFailure { error ->
                                            System.err.println("ALAC decode failed: ${error.message}")
                                        }.getOrNull() ?: return@audio

                                        runCatching { newAudioOutput.write(pcm) }
                                            .onFailure { error ->
                                                System.err.println("Windows audio write failed: ${error.message}")
                                            }
                                        packetCount += 1
                                        if (packetCount == 1L || packetCount % 10_000L == 0L) {
                                            println(
                                                "#$connectionId audio packets=$packetCount " +
                                                    "seq=${decrypted.sequenceNumber} timestamp=${decrypted.timestamp}"
                                            )
                                        }
                                    },
                                    observer = { channel, number, _, _ ->
                                        if (channel != "audio" && number == 1L) {
                                            println("#$connectionId first $channel UDP packet received")
                                        }
                                    }
                                )

                                println(
                                    "#$connectionId UDP: audio=${allocatedPorts.audioPort}, " +
                                        "control=${allocatedPorts.controlPort}, " +
                                        "timing=${allocatedPorts.timingPort}; " +
                                        "clientControl=${clientTransport.controlPort}, " +
                                        "clientTiming=${clientTransport.timingPort}"
                                )
                                sendResponse(
                                    output,
                                    cseq,
                                    extraHeaders = buildString {
                                        append("Transport: ${RaopTransport.serverHeader(allocatedPorts)}\r\n")
                                        append("Session: 1\r\n")
                                    }
                                )
                            }.onFailure { error ->
                                System.err.println("SETUP failed: ${error.message}")
                                sendResponse(
                                    output,
                                    cseq,
                                    statusCode = 451,
                                    reason = "Parameter Not Understood"
                                )
                            }
                        }

                        "RECORD" -> {
                            runCatching { requireNotNull(audioOutput).start() }
                                .onSuccess {
                                    nowPlayingState.playbackStarted(connectionId)
                                    println("#$connectionId Windows audio playback started")
                                }
                                .onFailure { error ->
                                    System.err.println("Audio start failed: ${error.message}")
                                }
                            sendResponse(output, cseq)
                        }

                        "FLUSH" -> {
                            runCatching { audioOutput?.flush() }
                                .onFailure { error ->
                                    System.err.println("Audio FLUSH failed: ${error.message}")
                                }
                            sendResponse(output, cseq)
                        }

                        "SET_PARAMETER" -> {
                            val shape = WindowsSenderDiagnostics.describeSetParameter(request)
                            if (observedSetParameterShapes.add(shape)) {
                                println("#$connectionId SET_PARAMETER shape: $shape")
                            }
                            handleSetParameter(request, connectionId, audioSession, audioOutput)
                            sendResponse(output, cseq)
                        }

                        "TEARDOWN" -> {
                            endReason = "teardown"
                            sendResponse(output, cseq)
                            break
                        }

                        else -> sendResponse(output, cseq)
                    }
                }
            }
        } catch (error: Exception) {
            endReason = if (running) "connection_error" else "shutdown"
            if (running) {
                System.err.println("RAOP connection #$connectionId failed: ${error.message}")
                error.printStackTrace(System.err)
            }
        } finally {
            runCatching { udpPorts?.close() }
            runCatching { audioOutput?.close() }
            connectionSockets -= client
            nowPlayingState.connectionClosed(connectionId)
            println("RAOP connection #$connectionId ended ($endReason)")
        }
    }

    private fun handleSetParameter(
        request: RtspRequest,
        connectionId: Long,
        audioSession: RaopAudioSession?,
        audioOutput: WindowsRaopAudioOutput?
    ) {
        val bodyType = request.header("Content-Type")
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?: return

        when (bodyType) {
            "text/parameters" -> {
                val parameters = runCatching {
                    RaopTextParametersParser.parse(
                        request.body,
                        audioSession?.format?.sampleRate ?: 44_100
                    )
                }.getOrElse { error ->
                    System.err.println("RAOP text parameters failed: ${error.message}")
                    return
                }

                parameters.volumeDb?.let { volumeDb ->
                    runCatching { audioOutput?.setVolumeDb(volumeDb) }
                        .onSuccess {
                            volumeStore.saveSenderVolume(volumeDb)
                            nowPlayingState.updateVolume(connectionId, volumeDb)
                        }
                }
                parameters.progress?.let { progress ->
                    metadataExecutor.execute {
                        nowPlayingState.updateProgress(connectionId, progress)
                    }
                }
            }

            "application/x-dmap-tagged" -> {
                val body = request.body
                metadataExecutor.execute {
                    runCatching { RaopDmapParser.parse(body) }
                        .onSuccess { metadata ->
                            nowPlayingState.applyMetadata(connectionId, metadata)
                            println(
                                "#$connectionId DMAP metadata parsed: " +
                                    WindowsSenderDiagnostics.describeMetadata(metadata)
                            )
                        }
                        .onFailure { error ->
                            System.err.println(
                                "DMAP metadata failed: ${error.message}; " +
                                    "root=${WindowsSenderDiagnostics.dmapRootTag(body)} bytes=${body.size}"
                            )
                        }
                }
            }

            "image/jpeg" -> {
                val body = request.body
                metadataExecutor.execute {
                    val artwork = runCatching {
                        WindowsArtworkDecoder.decodeJpeg(body, ARTWORK_TARGET_SIZE_PX)
                    }.onFailure { error ->
                        System.err.println("JPEG artwork decode failed: ${error.message}")
                    }.getOrNull()
                    nowPlayingState.updateArtwork(connectionId, artwork)
                }
            }

            "image/none" -> metadataExecutor.execute {
                nowPlayingState.updateArtwork(connectionId, null)
            }
        }
    }

    private fun sendResponse(
        output: OutputStream,
        cseq: String,
        extraHeaders: String = "",
        statusCode: Int = 200,
        reason: String = "OK"
    ) {
        val response = buildString {
            append("RTSP/1.0 $statusCode $reason\r\n")
            append("CSeq: $cseq\r\n")
            append("Server: AirTunes/105.1\r\n")
            append(extraHeaders)
            append("\r\n")
        }
        output.write(response.toByteArray(Charsets.ISO_8859_1))
        output.flush()
    }

    fun stop() {
        closeResources()
        nowPlayingState.receiverStopped()
    }

    @Synchronized
    private fun closeResources() {
        if (!running && serverSocket == null && advertiser == null) return
        running = false
        runCatching { advertiser?.close() }
        advertiser = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        connectionSockets.toList().forEach { socket -> runCatching { socket.close() } }
        connectionSockets.clear()
        connectionExecutor.shutdownNow()
        metadataExecutor.shutdownNow()
        if (!connectionExecutor.awaitTermination(EXECUTOR_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            System.err.println("RAOP connection executor did not stop within timeout")
        }
        if (!metadataExecutor.awaitTermination(EXECUTOR_STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            System.err.println("RAOP metadata executor did not stop within timeout")
        }
    }

    private fun hasSenderShape(request: RtspRequest): Boolean =
        request.header("User-Agent") != null ||
            request.header("Client-Instance") != null ||
            request.header("DACP-ID") != null ||
            request.header("Active-Remote") != null

    private companion object {
        const val EXECUTOR_STOP_TIMEOUT_SECONDS = 5L
    }
}
