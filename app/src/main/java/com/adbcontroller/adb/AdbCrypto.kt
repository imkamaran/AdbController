package com.adbcontroller.adb

import android.content.Context
import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

class AdbCrypto(private val keyPair: KeyPair) {

    val privateKey: PrivateKey get() = keyPair.private

    private var cachedCertificate: X509Certificate? = null

    /**
     * Self-signed certificate wrapping this ADB key, used for TLS pairing and connections.
     * The daemon trusts the key itself, so the certificate's details don't matter.
     */
    @Synchronized
    fun certificate(): X509Certificate {
        cachedCertificate?.let { return it }
        val now = System.currentTimeMillis()
        val subject = X500Name("CN=AdbController")
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(now),
            Date(now - DAY_MS),
            Date(now + 30L * 365 * DAY_MS),
            subject,
            keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        return JcaX509CertificateConverter().getCertificate(builder.build(signer))
            .also { cachedCertificate = it }
    }

    fun signToken(token: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA1withRSA")
        signature.initSign(keyPair.private)
        signature.update(token)
        return signature.sign()
    }

    fun getAdbPublicKey(hostname: String = "AdbController@android"): ByteArray {
        val encoded = Base64.encodeToString(adbKeyStruct(), Base64.NO_WRAP) + " $hostname\u0000"
        return encoded.toByteArray(Charsets.UTF_8)
    }

    /** Matches the MD5 fingerprint Android shows in its "Allow USB debugging?" prompt. */
    fun fingerprint(): String {
        val digest = java.security.MessageDigest.getInstance("MD5").digest(adbKeyStruct())
        return digest.joinToString(":") { "%02X".format(it) }
    }

    private fun adbKeyStruct(): ByteArray {
        val pubKey = keyPair.public as RSAPublicKey
        val modulus = pubKey.modulus
        val exponent = pubKey.publicExponent

        val words = modulusToWords(modulus)
        val r32 = BigInteger.ONE.shiftLeft(32)
        val n0inv = r32.subtract(modulus.modInverse(r32)).toLong() and 0xFFFFFFFFL

        val buf = java.nio.ByteBuffer.allocate(4 + 4 + 256 + 256 + 4)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)

        buf.putInt(64) // RSANUMWORDS
        buf.putInt(n0inv.toInt())

        for (w in words) buf.putInt(w)

        val rr = BigInteger.ONE.shiftLeft(4096).mod(modulus)
        val rrWords = modulusToWords(rr)
        for (w in rrWords) buf.putInt(w)

        buf.putInt(exponent.toInt())
        return buf.array()
    }

    private fun modulusToWords(value: BigInteger): IntArray {
        val words = IntArray(64)
        var remaining = value
        val mask = BigInteger.valueOf(0xFFFFFFFFL)
        for (i in 0 until 64) {
            words[i] = remaining.and(mask).toInt()
            remaining = remaining.shiftRight(32)
        }
        return words
    }

    companion object {
        private const val DAY_MS = 86_400_000L
        private const val KEY_FILE_PRIVATE = "adb_key"

        @Volatile
        private var shared: AdbCrypto? = null

        /** The app's single ADB identity, shared by the UI and background pairing. */
        fun get(context: Context): AdbCrypto =
            shared ?: synchronized(this) {
                shared ?: loadOrGenerate(context.applicationContext).also { shared = it }
            }
        private const val KEY_FILE_PUBLIC = "adb_key.pub"

        fun loadOrGenerate(context: Context): AdbCrypto {
            val privateFile = File(context.filesDir, KEY_FILE_PRIVATE)
            val publicFile = File(context.filesDir, KEY_FILE_PUBLIC)

            if (privateFile.exists() && publicFile.exists()) {
                try {
                    val keyFactory = KeyFactory.getInstance("RSA")
                    val privateKey = keyFactory.generatePrivate(
                        PKCS8EncodedKeySpec(privateFile.readBytes())
                    )
                    val publicKey = keyFactory.generatePublic(
                        X509EncodedKeySpec(publicFile.readBytes())
                    )
                    return AdbCrypto(KeyPair(publicKey, privateKey))
                } catch (_: Exception) {
                    // Regenerate on any error
                }
            }

            val keyPair = generate()
            privateFile.writeBytes(keyPair.keyPair.private.encoded)
            publicFile.writeBytes(keyPair.keyPair.public.encoded)
            return keyPair
        }

        fun generate(): AdbCrypto {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
            return AdbCrypto(generator.generateKeyPair())
        }
    }
}
