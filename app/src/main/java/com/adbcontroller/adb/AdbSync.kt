package com.adbcontroller.adb

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbSync private constructor(private val stream: AdbStream) {

    private var buffer = ByteArray(0)
    private var bufferPos = 0

    suspend fun push(
        input: InputStream,
        remotePath: String,
        mode: Int = 420,
        onProgress: (Long) -> Unit = {}
    ) {
        val pathAndMode = "$remotePath,${S_IFREG or mode}".toByteArray(Charsets.UTF_8)
        stream.write(header("SEND", pathAndMode.size) + pathAndMode)

        val chunk = ByteArray(SYNC_DATA_MAX)
        var sent = 0L
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            if (n == 0) continue
            stream.write(header("DATA", n) + chunk.copyOf(n))
            sent += n
            onProgress(sent)
        }
        stream.write(header("DONE", (System.currentTimeMillis() / 1000).toInt()))

        val (id, len) = readHeader()
        when (id) {
            "OKAY" -> Unit
            "FAIL" -> throw AdbProtocolException(String(readExact(len), Charsets.UTF_8))
            else -> throw AdbProtocolException("Unexpected sync response: $id")
        }
    }

    suspend fun pull(remotePath: String, output: OutputStream, onProgress: (Long) -> Unit = {}): Long {
        val path = remotePath.toByteArray(Charsets.UTF_8)
        stream.write(header("RECV", path.size) + path)

        var total = 0L
        while (true) {
            val (id, len) = readHeader()
            when (id) {
                "DATA" -> {
                    output.write(readExact(len))
                    total += len
                    onProgress(total)
                }
                "DONE" -> return total
                "FAIL" -> throw AdbProtocolException(String(readExact(len), Charsets.UTF_8))
                else -> throw AdbProtocolException("Unexpected sync response: $id")
            }
        }
    }

    /** Returns file size in bytes, or -1 if the path does not exist. */
    suspend fun statSize(remotePath: String): Long {
        val path = remotePath.toByteArray(Charsets.UTF_8)
        stream.write(header("STAT", path.size) + path)
        val response = readExact(16)
        val buf = ByteBuffer.wrap(response).order(ByteOrder.LITTLE_ENDIAN)
        val id = String(response, 0, 4, Charsets.US_ASCII)
        if (id != "STAT") throw AdbProtocolException("Unexpected sync response: $id")
        buf.position(4)
        val mode = buf.int
        val size = buf.int.toLong() and 0xFFFFFFFFL
        return if (mode == 0) -1 else size
    }

    private suspend fun readHeader(): Pair<String, Int> {
        val h = readExact(8)
        val id = String(h, 0, 4, Charsets.US_ASCII)
        val len = ByteBuffer.wrap(h, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return id to len
    }

    private suspend fun readExact(n: Int): ByteArray {
        val out = ByteArray(n)
        var filled = 0
        while (filled < n) {
            if (bufferPos >= buffer.size) {
                buffer = stream.read()
                bufferPos = 0
                continue
            }
            val take = minOf(n - filled, buffer.size - bufferPos)
            System.arraycopy(buffer, bufferPos, out, filled, take)
            bufferPos += take
            filled += take
        }
        return out
    }

    private fun header(id: String, value: Int): ByteArray =
        ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .put(id.toByteArray(Charsets.US_ASCII))
            .putInt(value)
            .array()

    companion object {
        private const val SYNC_DATA_MAX = 64 * 1024
        private const val S_IFREG = 0x8000

        suspend fun <T> use(connection: AdbConnection, block: suspend (AdbSync) -> T): T {
            val stream = connection.open("sync:")
            val sync = AdbSync(stream)
            try {
                return block(sync)
            } finally {
                try {
                    stream.write(sync.header("QUIT", 0))
                } catch (_: Exception) {
                }
                stream.close()
            }
        }
    }
}
