package com.adbcontroller.adb

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AdbMessage(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val data: ByteArray = ByteArray(0)
) {
    val magic: Int get() = command xor -0x1

    companion object {
        const val A_CNXN = 0x4e584e43
        const val A_AUTH = 0x48545541
        const val A_OPEN = 0x4e45504f
        const val A_OKAY = 0x59414b4f
        const val A_CLSE = 0x45534c43
        const val A_WRTE = 0x45545257

        const val AUTH_TOKEN = 1
        const val AUTH_SIGNATURE = 2
        const val AUTH_RSAPUBLICKEY = 3

        const val A_VERSION = 0x01000000
        const val MAX_PAYLOAD = 1024 * 1024

        const val HEADER_SIZE = 24

        fun read(input: InputStream): AdbMessage {
            val header = ByteArray(HEADER_SIZE)
            readFully(input, header)

            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val command = buf.int
            val arg0 = buf.int
            val arg1 = buf.int
            val dataLength = buf.int
            val dataCrc = buf.int
            val magic = buf.int

            if (command xor -0x1 != magic) {
                throw AdbProtocolException("Invalid magic: expected ${command xor -0x1}, got $magic")
            }

            val data = if (dataLength > 0) {
                if (dataLength > MAX_PAYLOAD) {
                    throw AdbProtocolException("Payload too large: $dataLength")
                }
                ByteArray(dataLength).also { readFully(input, it) }
            } else {
                ByteArray(0)
            }

            val actualCrc = checksum(data)
            if (actualCrc != dataCrc) {
                throw AdbProtocolException("CRC mismatch: expected $dataCrc, got $actualCrc")
            }

            return AdbMessage(command, arg0, arg1, data)
        }

        private fun readFully(input: InputStream, buffer: ByteArray) {
            var offset = 0
            while (offset < buffer.size) {
                val read = input.read(buffer, offset, buffer.size - offset)
                if (read < 0) throw AdbProtocolException("Unexpected end of stream")
                offset += read
            }
        }

        fun checksum(data: ByteArray): Int {
            var sum = 0
            for (b in data) {
                sum += b.toInt() and 0xFF
            }
            return sum
        }
    }

    fun write(output: OutputStream) {
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(command)
        header.putInt(arg0)
        header.putInt(arg1)
        header.putInt(data.size)
        header.putInt(checksum(data))
        header.putInt(magic)
        output.write(header.array())
        if (data.isNotEmpty()) {
            output.write(data)
        }
        output.flush()
    }

    fun commandName(): String = when (command) {
        A_CNXN -> "CNXN"
        A_AUTH -> "AUTH"
        A_OPEN -> "OPEN"
        A_OKAY -> "OKAY"
        A_CLSE -> "CLSE"
        A_WRTE -> "WRTE"
        else -> "UNKNOWN(0x${command.toString(16)})"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AdbMessage) return false
        return command == other.command && arg0 == other.arg0 &&
                arg1 == other.arg1 && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = command
        result = 31 * result + arg0
        result = 31 * result + arg1
        result = 31 * result + data.contentHashCode()
        return result
    }
}

class AdbProtocolException(message: String) : Exception(message)
