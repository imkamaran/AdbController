package com.adbcontroller.adb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.*
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class AdbConnection private constructor(
    private val crypto: AdbCrypto,
    private val transport: AdbTransport
) : Closeable {

    private val streams = ConcurrentHashMap<Int, AdbStream>()
    private val nextLocalId = AtomicInteger(1)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var maxData = 4096
        private set

    @Volatile
    var isConnected = false
        private set

    @Volatile
    var deviceBanner = ""
        private set

    private var readerJob: Job? = null
    private val closed = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    var onClosed: (() -> Unit)? = null

    suspend fun connect() {
        val banner = "host::features=cmd,stat_v2,ls_v2,fixed_push_mkdir,apex,abb,abb_exec"
        val cnxn = AdbMessage(
            AdbMessage.A_CNXN,
            AdbMessage.A_VERSION,
            LOCAL_MAX_DATA,
            banner.toByteArray(Charsets.UTF_8)
        )
        sendMessage(cnxn)

        // ADB auth handshake. The daemon replies to CNXN with AUTH(TOKEN); we sign it with our
        // private key. If the key isn't trusted yet the daemon re-sends AUTH(TOKEN), at which point
        // we send our public key so the user can approve it. After approval the daemon issues a
        // *fresh* token to be signed with the now-trusted key, and only then sends CNXN — so this
        // must be a loop, not a fixed sequence, for the first connection to a device to succeed.
        var sentSignature = false
        var sentPublicKey = false
        var authRounds = 0

        while (true) {
            val response = readMessage()
            when (response.command) {
                AdbMessage.A_CNXN -> {
                    transport.setReadTimeout(0)
                    maxData = response.arg1
                    deviceBanner = String(response.data, Charsets.UTF_8)
                    isConnected = true
                    readerJob = scope.launch { messageLoop() }
                    return
                }
                AdbMessage.A_AUTH -> {
                    if (response.arg0 != AdbMessage.AUTH_TOKEN) {
                        throw AdbProtocolException("Unexpected AUTH response (arg0=${response.arg0})")
                    }
                    if (++authRounds > MAX_AUTH_ROUNDS) {
                        throw AdbProtocolException("Authentication failed - device did not accept the key")
                    }
                    when {
                        !sentSignature -> {
                            sendMessage(
                                AdbMessage(AdbMessage.A_AUTH, AdbMessage.AUTH_SIGNATURE, 0, crypto.signToken(response.data))
                            )
                            sentSignature = true
                        }
                        !sentPublicKey -> {
                            sendMessage(
                                AdbMessage(AdbMessage.A_AUTH, AdbMessage.AUTH_RSAPUBLICKEY, 0, crypto.getAdbPublicKey())
                            )
                            sentPublicKey = true
                            // The user now has to approve the key on the target; allow time for the dialog.
                            transport.setReadTimeout(AUTH_PROMPT_TIMEOUT_MS)
                        }
                        else -> {
                            // Key approved: sign the fresh token the daemon issued after approval.
                            sendMessage(
                                AdbMessage(AdbMessage.A_AUTH, AdbMessage.AUTH_SIGNATURE, 0, crypto.signToken(response.data))
                            )
                        }
                    }
                }
                else -> throw AdbProtocolException("Expected CNXN or AUTH, got ${response.commandName()}")
            }
        }
    }

    suspend fun open(destination: String): AdbStream {
        val localId = nextLocalId.getAndIncrement()
        val stream = AdbStream(localId, 0, this)
        streams[localId] = stream

        val payload = "$destination\u0000".toByteArray(Charsets.UTF_8)
        sendMessage(AdbMessage(AdbMessage.A_OPEN, localId, 0, payload))

        var waited = 0
        while (stream.remoteId == 0 && !stream.isClosed && waited < 10000) {
            delay(10)
            waited += 10
        }

        if (stream.isClosed) {
            streams.remove(localId)
            throw AdbProtocolException("Stream rejected by remote")
        }
        if (stream.remoteId == 0) {
            streams.remove(localId)
            throw AdbProtocolException("Timeout opening stream to $destination")
        }

        return stream
    }

    suspend fun shellCommand(command: String, timeoutMs: Long = 15_000): String {
        val stream = open("shell:$command")
        return try {
            stream.readAll(timeoutMs)
        } finally {
            stream.close()
        }
    }

    /** Runs a command over the raw exec: service and returns its binary stdout. */
    suspend fun execBytes(command: String, timeoutMs: Long = 20_000): ByteArray {
        val stream = open("exec:$command")
        val out = java.io.ByteArrayOutputStream()
        try {
            withTimeout(timeoutMs) {
                while (true) {
                    val data = stream.incoming.receiveCatching().getOrNull() ?: break
                    out.write(data)
                }
            }
        } finally {
            stream.close()
        }
        return out.toByteArray()
    }

    suspend fun service(destination: String, timeoutMs: Long = 5_000): String {
        val stream = open(destination)
        return try {
            stream.readAll(timeoutMs)
        } finally {
            stream.close()
        }
    }

    suspend fun interactiveShell(): AdbStream = open("shell:")

    internal fun sendMessage(message: AdbMessage) {
        transport.write(message)
    }

    internal fun sendOkay(localId: Int, remoteId: Int) {
        try {
            transport.write(AdbMessage(AdbMessage.A_OKAY, localId, remoteId))
        } catch (_: Exception) {
        }
    }

    private fun readMessage(): AdbMessage = transport.read()

    private suspend fun messageLoop() {
        try {
            while (isConnected) {
                val message = withContext(Dispatchers.IO) { readMessage() }
                handleMessage(message)
            }
        } catch (_: Exception) {
            disconnect()
        }
    }

    private fun handleMessage(message: AdbMessage) {
        when (message.command) {
            AdbMessage.A_OKAY -> {
                val stream = streams[message.arg1]
                if (stream != null) {
                    if (stream.remoteId == 0) {
                        stream.remoteId = message.arg0
                    } else {
                        stream.handleOkay()
                    }
                }
            }
            AdbMessage.A_WRTE -> {
                val stream = streams[message.arg1]
                stream?.handleData(message.data)
            }
            AdbMessage.A_CLSE -> {
                val stream = streams[message.arg1]
                if (stream != null) {
                    stream.handleClose()
                    streams.remove(message.arg1)
                }
            }
        }
    }

    internal fun closeStream(stream: AdbStream) {
        streams.remove(stream.localId)
        try {
            sendMessage(AdbMessage(AdbMessage.A_CLSE, stream.localId, stream.remoteId))
        } catch (_: Exception) {
        }
    }

    fun disconnect() {
        if (!closed.compareAndSet(false, true)) return
        isConnected = false
        readerJob?.cancel()
        streams.values.forEach { it.handleClose() }
        streams.clear()
        transport.close()
        onClosed?.invoke()
    }

    override fun close() = disconnect()

    companion object {
        private const val LOCAL_MAX_DATA = 256 * 1024
        private const val AUTH_PROMPT_TIMEOUT_MS = 60_000
        private const val MAX_AUTH_ROUNDS = 20

        suspend fun connectTcp(
            host: String,
            port: Int,
            crypto: AdbCrypto,
            timeoutMs: Int = 10000
        ): AdbConnection {
            val socket = withContext(Dispatchers.IO) {
                Socket().apply {
                    soTimeout = timeoutMs
                    connect(InetSocketAddress(host, port), timeoutMs)
                }
            }
            val transport = TcpTransport(socket)
            val connection = AdbConnection(crypto, transport)
            connection.connect()
            return connection
        }

        fun connectUsb(
            usbManager: UsbManager,
            device: UsbDevice,
            crypto: AdbCrypto
        ): AdbConnection {
            var adbInterface: UsbInterface? = null
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == 0xFF &&
                    iface.interfaceSubclass == 0x42 &&
                    iface.interfaceProtocol == 0x01) {
                    adbInterface = iface
                    break
                }
            }
            if (adbInterface == null) {
                throw AdbProtocolException("No ADB interface found on USB device")
            }

            val deviceConnection = usbManager.openDevice(device)
                ?: throw AdbProtocolException("Failed to open USB device")

            if (!deviceConnection.claimInterface(adbInterface, true)) {
                throw AdbProtocolException("Failed to claim ADB interface")
            }

            var inEndpoint: UsbEndpoint? = null
            var outEndpoint: UsbEndpoint? = null
            for (i in 0 until adbInterface.endpointCount) {
                val ep = adbInterface.getEndpoint(i)
                if (ep.direction == android.hardware.usb.UsbConstants.USB_DIR_IN) {
                    inEndpoint = ep
                } else {
                    outEndpoint = ep
                }
            }

            if (inEndpoint == null || outEndpoint == null) {
                throw AdbProtocolException("Missing USB endpoints")
            }

            val transport = UsbTransport(deviceConnection, inEndpoint, outEndpoint)
            return AdbConnection(crypto, transport)
        }
    }
}

interface AdbTransport : Closeable {
    fun read(): AdbMessage
    fun write(message: AdbMessage)

    /** 0 means wait indefinitely. */
    fun setReadTimeout(ms: Int)
}

class TcpTransport(private val socket: Socket) : AdbTransport {
    private val input: InputStream = socket.getInputStream()
    private val output: OutputStream = socket.getOutputStream()

    override fun setReadTimeout(ms: Int) {
        socket.soTimeout = ms
    }

    override fun read(): AdbMessage = synchronized(input) { AdbMessage.read(input) }

    override fun write(message: AdbMessage) = synchronized(output) { message.write(output) }

    override fun close() {
        try { socket.close() } catch (_: Exception) {}
    }
}

class UsbTransport(
    private val connection: UsbDeviceConnection,
    private val inEndpoint: UsbEndpoint,
    private val outEndpoint: UsbEndpoint
) : AdbTransport {
    private val timeout = 10000

    @Volatile
    private var readTimeout = 10000

    override fun setReadTimeout(ms: Int) {
        readTimeout = ms
    }

    override fun read(): AdbMessage {
        val header = ByteArray(AdbMessage.HEADER_SIZE)
        val headerRead = connection.bulkTransfer(inEndpoint, header, header.size, readTimeout)
        if (headerRead != AdbMessage.HEADER_SIZE) {
            throw AdbProtocolException("USB header read failed: $headerRead")
        }

        val buf = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val command = buf.int
        val arg0 = buf.int
        val arg1 = buf.int
        val dataLength = buf.int
        buf.int // crc
        buf.int // magic

        val data = if (dataLength > 0) {
            val payload = ByteArray(dataLength)
            var offset = 0
            while (offset < dataLength) {
                val read = connection.bulkTransfer(
                    inEndpoint, payload, offset, dataLength - offset, timeout
                )
                if (read < 0) throw AdbProtocolException("USB data read failed")
                offset += read
            }
            payload
        } else {
            ByteArray(0)
        }

        return AdbMessage(command, arg0, arg1, data)
    }

    override fun write(message: AdbMessage) {
        val header = java.nio.ByteBuffer.allocate(AdbMessage.HEADER_SIZE)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.putInt(message.command)
        header.putInt(message.arg0)
        header.putInt(message.arg1)
        header.putInt(message.data.size)
        header.putInt(AdbMessage.checksum(message.data))
        header.putInt(message.magic)

        val headerSent = connection.bulkTransfer(outEndpoint, header.array(), AdbMessage.HEADER_SIZE, timeout)
        if (headerSent != AdbMessage.HEADER_SIZE) {
            throw AdbProtocolException("USB header write failed")
        }

        if (message.data.isNotEmpty()) {
            val dataSent = connection.bulkTransfer(outEndpoint, message.data, message.data.size, timeout)
            if (dataSent != message.data.size) {
                throw AdbProtocolException("USB data write failed")
            }
        }
    }

    override fun close() {
        try { connection.close() } catch (_: Exception) {}
    }
}
