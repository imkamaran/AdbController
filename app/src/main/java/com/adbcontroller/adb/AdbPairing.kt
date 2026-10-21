package com.adbcontroller.adb

import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

data class PairingResult(
    val success: Boolean,
    val message: String
)

class AdbPairing(private val crypto: AdbCrypto) {

    companion object {
        private fun hexToBytes(hex: String): ByteArray {
            val result = ByteArray(hex.length / 2)
            for (i in result.indices) {
                result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return result
        }

        private val SPAKE2_M = hexToBytes(
            "04886e2f97ace46e55ba9dd7242579f2993b64e16ef3dcab95afd497333d8fa12f" +
            "5ff355163e43ce224e0b0e65ff02ac8e5c7be09419c785e0ca547d55a12e2d20"
        )

        private val SPAKE2_N = hexToBytes(
            "04d8bbd6c639c62937b04d997f38c3770719c629d7014d49a24b4f98baa1292b49" +
            "07d60aa6bfade45008a636337f5168c64d9bd36034808cd564490b1e656edbe7"
        )

        private val CLIENT_NAME = "adb pair client".toByteArray()
        private val SERVER_NAME = "adb pair server".toByteArray()

        private const val HEADER_VERSION: Byte = 1
        private const val MSG_SPAKE2: Byte = 0
        private const val MSG_PEER_INFO: Byte = 1
        private const val PEER_INFO_ADB_KEY: Byte = 0
        private const val MAX_PEER_INFO_SIZE = 8192 + 1
        private const val GCM_NONCE_SIZE = 12
        private const val GCM_TAG_BITS = 128
        private const val AES_KEY_LEN = 16
    }

    private val ecSpec = ECNamedCurveTable.getParameterSpec("P-256")
    private val curve = ecSpec.curve
    private val basePoint = ecSpec.g
    private val order = ecSpec.n
    private val pointM = curve.decodePoint(SPAKE2_M)
    private val pointN = curve.decodePoint(SPAKE2_N)

    private var encCounter = 0L

    fun pair(host: String, port: Int, pairingCode: String): PairingResult {
        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(TrustAllManager()), SecureRandom())
            val rawSocket = java.net.Socket()
            rawSocket.connect(java.net.InetSocketAddress(host, port), 10000)
            rawSocket.soTimeout = 10000
            val socket = sslContext.socketFactory.createSocket(
                rawSocket, host, port, true
            ) as SSLSocket
            socket.useClientMode = true
            socket.startHandshake()

            val input = socket.inputStream
            val output = socket.outputStream

            val password = pairingCode.toByteArray(Charsets.UTF_8)
            val w = passwordToScalar(password)
            val random = SecureRandom()
            var x: BigInteger
            do {
                x = BigInteger(256, random).mod(order)
            } while (x == BigInteger.ZERO)

            val T = basePoint.multiply(x).add(pointM.multiply(w)).normalize()
            val myMsg = T.getEncoded(false)

            sendPairingMsg(output, MSG_SPAKE2, myMsg)

            val (serverType, serverMsg) = readPairingMsg(input)
            if (serverType != MSG_SPAKE2) {
                socket.close()
                return PairingResult(false, "Unexpected message type during SPAKE2 exchange")
            }

            val S = curve.decodePoint(serverMsg)
            val K = S.subtract(pointN.multiply(w)).multiply(x).normalize()

            val wBytes = scalarToBytes32(w)
            val spake2Key = computeTranscriptHash(
                myMsg, serverMsg, K.getEncoded(false), wBytes
            )

            val aesKey = hkdf(
                spake2Key, null,
                "adb pairing_auth aes-128-gcm key".toByteArray(),
                AES_KEY_LEN
            )

            val peerInfo = buildPeerInfo()
            val encrypted = aesGcmEncrypt(aesKey, peerInfo)
            sendPairingMsg(output, MSG_PEER_INFO, encrypted)

            val (infoType, encServerInfo) = readPairingMsg(input)
            if (infoType != MSG_PEER_INFO) {
                socket.close()
                return PairingResult(false, "Expected PEER_INFO response")
            }

            val decrypted = aesGcmDecrypt(aesKey, encServerInfo)
            socket.close()

            if (decrypted == null) {
                return PairingResult(false, "Decryption failed - wrong pairing code?")
            }

            return PairingResult(true, "Device paired successfully! You can now connect.")

        } catch (e: javax.net.ssl.SSLHandshakeException) {
            return PairingResult(false, "TLS handshake failed: ${e.message}")
        } catch (e: java.net.ConnectException) {
            return PairingResult(false, "Cannot reach $host:$port - check IP and pairing port")
        } catch (e: java.net.SocketTimeoutException) {
            return PairingResult(false, "Connection timed out - check IP and pairing port")
        } catch (e: Exception) {
            return PairingResult(false, "Pairing failed: ${e.message}")
        }
    }

    private fun passwordToScalar(password: ByteArray): BigInteger {
        val derived = hkdf(
            password, null,
            "SPAKE2 hash".toByteArray(),
            64
        )
        return BigInteger(1, derived).mod(order)
    }

    private fun computeTranscriptHash(
        myMsg: ByteArray, theirMsg: ByteArray,
        kBytes: ByteArray, wBytes: ByteArray
    ): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        addLenPrefixed(digest, CLIENT_NAME)
        addLenPrefixed(digest, SERVER_NAME)
        addLenPrefixed(digest, myMsg)
        addLenPrefixed(digest, theirMsg)
        addLenPrefixed(digest, kBytes)
        addLenPrefixed(digest, wBytes)
        return digest.digest()
    }

    private fun addLenPrefixed(digest: MessageDigest, data: ByteArray) {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(data.size)
        digest.update(buf.array())
        digest.update(data)
    }

    private fun scalarToBytes32(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        val result = ByteArray(32)
        if (raw.size <= 32) {
            raw.copyInto(result, 32 - raw.size)
        } else {
            raw.copyInto(result, 0, raw.size - 32, raw.size)
        }
        return result
    }

    private fun hkdf(ikm: ByteArray, salt: ByteArray?, info: ByteArray, length: Int): ByteArray {
        val gen = HKDFBytesGenerator(SHA256Digest())
        gen.init(HKDFParameters(ikm, salt, info))
        val output = ByteArray(length)
        gen.generateBytes(output, 0, length)
        return output
    }

    private fun buildPeerInfo(): ByteArray {
        val pubKey = crypto.getAdbPublicKey()
        val peerInfo = ByteArray(MAX_PEER_INFO_SIZE)
        peerInfo[0] = PEER_INFO_ADB_KEY
        val copyLen = minOf(pubKey.size, MAX_PEER_INFO_SIZE - 1)
        pubKey.copyInto(peerInfo, 1, 0, copyLen)
        return peerInfo
    }

    private fun aesGcmEncrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(GCM_NONCE_SIZE)
        ByteBuffer.wrap(nonce).order(ByteOrder.LITTLE_ENDIAN).putLong(encCounter++)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext)
        return nonce + ciphertext
    }

    private fun aesGcmDecrypt(key: ByteArray, data: ByteArray): ByteArray? {
        if (data.size < GCM_NONCE_SIZE) return null
        val nonce = data.copyOfRange(0, GCM_NONCE_SIZE)
        val ciphertext = data.copyOfRange(GCM_NONCE_SIZE, data.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }

    private fun sendPairingMsg(output: java.io.OutputStream, type: Byte, payload: ByteArray) {
        val header = ByteArray(6)
        header[0] = HEADER_VERSION
        header[1] = type
        ByteBuffer.wrap(header, 2, 4).order(ByteOrder.BIG_ENDIAN).putInt(payload.size)
        output.write(header)
        output.write(payload)
        output.flush()
    }

    private fun readPairingMsg(input: java.io.InputStream): Pair<Byte, ByteArray> {
        val header = readFully(input, 6)
        val type = header[1]
        val payloadSize = ByteBuffer.wrap(header, 2, 4).order(ByteOrder.BIG_ENDIAN).int
        if (payloadSize < 0 || payloadSize > 1_000_000) {
            throw java.io.IOException("Invalid pairing payload size: $payloadSize")
        }
        val payload = readFully(input, payloadSize)
        return Pair(type, payload)
    }

    private fun readFully(input: java.io.InputStream, length: Int): ByteArray {
        val buf = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(buf, offset, length - offset)
            if (read < 0) throw java.io.IOException("Unexpected end of stream")
            offset += read
        }
        return buf
    }

    private class TrustAllManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
