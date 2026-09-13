package com.clipnest.data.repository

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@JsonClass(generateAdapter = true)
data class EncryptedDocument(
    val format: String = FORMAT,
    val version: Int = VERSION,
    val cipher: String = CIPHER,
    val kdf: String = KDF,
    val iterations: Int,
    val salt: String,
    val nonce: String,
    val ciphertext: String
) {
    companion object {
        const val FORMAT = "clipnest-encrypted-document"
        const val VERSION = 1
        const val CIPHER = "AES-256-GCM"
        const val KDF = "PBKDF2WithHmacSHA256"
    }
}

object EncryptedDocumentCodec {
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val NONCE_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128
    private const val PBKDF2_ITERATIONS = 210_000

    private val secureRandom = SecureRandom()
    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(EncryptedDocument::class.java).indent("  ")
    private val base64Encoder = Base64.getEncoder()
    private val base64Decoder = Base64.getDecoder()

    /**
     * Detects whether [text] is a document encrypted by ClipNest, as opposed to
     * an arbitrary JSON file the user opened. Only inspects the `format` field
     * and never throws: any parse failure (not JSON, unrelated JSON shape, etc.)
     * is treated as "not an encrypted ClipNest document".
     */
    fun isEncryptedDocument(text: String): Boolean =
        runCatching { adapter.fromJson(text)?.format == EncryptedDocument.FORMAT }.getOrDefault(false)

    fun encode(content: String, password: String): String {
        require(password.isNotEmpty()) { "Password must not be empty" }
        val salt = ByteArray(SALT_LENGTH_BYTES).also(secureRandom::nextBytes)
        val nonce = ByteArray(NONCE_LENGTH_BYTES).also(secureRandom::nextBytes)
        val key = deriveKey(password, salt, PBKDF2_ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, nonce))
        cipher.updateAAD(envelopeAad())
        val ciphertext = cipher.doFinal(content.toByteArray(StandardCharsets.UTF_8))
        return adapter.toJson(
            EncryptedDocument(
                iterations = PBKDF2_ITERATIONS,
                salt = base64Encoder.encodeToString(salt),
                nonce = base64Encoder.encodeToString(nonce),
                ciphertext = base64Encoder.encodeToString(ciphertext)
            )
        )
    }

    fun decode(json: String, password: String): String {
        val envelope = adapter.fromJson(json) ?: throw IllegalArgumentException("Encrypted document is empty")
        require(envelope.format == EncryptedDocument.FORMAT) { "Unsupported encrypted document format" }
        require(envelope.version == EncryptedDocument.VERSION) { "Unsupported encrypted document version" }
        require(envelope.cipher == EncryptedDocument.CIPHER) { "Unsupported encryption cipher" }
        require(envelope.kdf == EncryptedDocument.KDF) { "Unsupported key derivation function" }
        require(envelope.iterations in 100_000..1_000_000) { "Unsupported key derivation cost" }
        return try {
            val salt = base64Decoder.decode(envelope.salt)
            val nonce = base64Decoder.decode(envelope.nonce)
            val ciphertext = base64Decoder.decode(envelope.ciphertext)
            require(salt.size == SALT_LENGTH_BYTES) { "Invalid document salt" }
            require(nonce.size == NONCE_LENGTH_BYTES) { "Invalid document nonce" }
            require(ciphertext.size > TAG_LENGTH_BITS / 8) { "Invalid document ciphertext" }
            val key = deriveKey(password, salt, envelope.iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, nonce))
            cipher.updateAAD(envelopeAad())
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid password or encrypted document", error)
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return try {
            val secret = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
            SecretKeySpec(secret.encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun envelopeAad(): ByteArray =
        "${EncryptedDocument.FORMAT}:${EncryptedDocument.VERSION}:${EncryptedDocument.CIPHER}:${EncryptedDocument.KDF}"
            .toByteArray(StandardCharsets.UTF_8)
}
