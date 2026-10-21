package com.adbcontroller.adb

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import java.io.Closeable

class AdbStream(
    val localId: Int,
    var remoteId: Int = 0,
    private val connection: AdbConnection
) : Closeable {

    val incoming = Channel<ByteArray>(Channel.UNLIMITED)
    private val acks = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    var isClosed = false
        private set

    suspend fun write(data: ByteArray) {
        if (isClosed) throw AdbProtocolException("Stream closed")

        var offset = 0
        while (offset < data.size) {
            val end = minOf(offset + connection.maxData, data.size)
            val chunk = data.copyOfRange(offset, end)
            connection.sendMessage(AdbMessage(AdbMessage.A_WRTE, localId, remoteId, chunk))
            waitForOkay()
            offset = end
        }
    }

    suspend fun read(): ByteArray =
        incoming.receiveCatching().getOrNull() ?: throw AdbProtocolException("Stream closed")

    suspend fun readString(): String = String(read(), Charsets.UTF_8)

    suspend fun readAll(timeoutMs: Long = 5000): String {
        val sb = StringBuilder()
        try {
            withTimeout(timeoutMs) {
                while (true) {
                    val data = incoming.receiveCatching().getOrNull() ?: break
                    sb.append(String(data, Charsets.UTF_8))
                }
            }
        } catch (_: TimeoutCancellationException) {
        }
        return sb.toString()
    }

    private suspend fun waitForOkay() {
        try {
            withTimeout(10_000) { acks.receive() }
        } catch (_: TimeoutCancellationException) {
            throw AdbProtocolException("Timeout waiting for OKAY")
        } catch (e: Exception) {
            if (isClosed) throw AdbProtocolException("Stream closed")
            throw e
        }
    }

    internal fun handleOkay() {
        acks.trySend(Unit)
    }

    internal fun handleData(data: ByteArray) {
        incoming.trySend(data)
        connection.sendOkay(localId, remoteId)
    }

    fun handleClose() {
        isClosed = true
        incoming.close()
        acks.close()
    }

    override fun close() {
        if (!isClosed) {
            handleClose()
            connection.closeStream(this)
        }
    }
}
