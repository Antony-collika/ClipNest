package com.clipnest.data.repository

import com.clipnest.data.model.ClipboardCard
import com.clipnest.data.model.ENCRYPTED_BACKUP_FORMAT
import com.clipnest.data.model.ENCRYPTED_BACKUP_VERSION
import com.clipnest.data.model.EncryptedVaultBackup
import com.clipnest.data.model.VaultBackupCard
import com.clipnest.data.model.VaultBackupFile
import com.clipnest.data.model.VAULT_BACKUP_FORMAT
import com.clipnest.data.model.VAULT_BACKUP_VERSION
import com.squareup.moshi.Moshi
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object VaultBackupCodec {
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH_BYTES = 16
    private const val NONCE_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128
    private const val PBKDF2_ITERATIONS = 210_000

    private val secureRandom = SecureRandom()
    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(VaultBackupFile::class.java).indent("  ")
    private val encryptedAdapter = moshi.adapter(EncryptedVaultBackup::class.java).indent("  ")
    private val base64Encoder = Base64.getEncoder()
    private val base64Decoder = Base64.getDecoder()

    fun encode(cards: List<ClipboardCard>): String = buildBackup(cards).let(adapter::toJson)

    fun encodeEncrypted(cards: List<ClipboardCard>, password: String): String {
        val plaintext = encode(cards).toByteArray(StandardCharsets.UTF_8)
        val salt = ByteArray(SALT_LENGTH_BYTES).also(secureRandom::nextBytes)
        val nonce = ByteArray(NONCE_LENGTH_BYTES).also(secureRandom::nextBytes)
        val key = deriveKey(password, salt, PBKDF2_ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, nonce))
        cipher.updateAAD(envelopeAad())
        val ciphertext = cipher.doFinal(plaintext)
        return encryptedAdapter.toJson(
            EncryptedVaultBackup(
                iterations = PBKDF2_ITERATIONS,
                salt = base64Encoder.encodeToString(salt),
                nonce = base64Encoder.encodeToString(nonce),
                ciphertext = base64Encoder.encodeToString(ciphertext)
            )
        )
    }

    fun decode(json: String): VaultBackupFile {
        val backup = adapter.fromJson(json)
            ?: throw IllegalArgumentException("Backup file is empty")
        require(backup.format == VAULT_BACKUP_FORMAT || backup.format == "xboard-backup") {
            "Unsupported backup format"
        }
        require(backup.version in 1..VAULT_BACKUP_VERSION) { "Unsupported backup version" }
        return backup
    }

    fun decodeEncrypted(json: String, password: String): VaultBackupFile {
        val envelope = encryptedAdapter.fromJson(json)
            ?: throw IllegalArgumentException("Backup file is empty")
        require(envelope.format == ENCRYPTED_BACKUP_FORMAT) { "Unsupported encrypted backup format" }
        require(envelope.version == ENCRYPTED_BACKUP_VERSION) { "Unsupported encrypted backup version" }
        require(envelope.cipher == "AES-256-GCM") { "Unsupported encryption cipher" }
        require(envelope.kdf == "PBKDF2WithHmacSHA256") { "Unsupported key derivation function" }
        require(envelope.iterations in 100_000..1_000_000) { "Unsupported key derivation cost" }

        return try {
            val salt = base64Decoder.decode(envelope.salt)
            val nonce = base64Decoder.decode(envelope.nonce)
            val ciphertext = base64Decoder.decode(envelope.ciphertext)
            require(salt.size == SALT_LENGTH_BYTES) { "Invalid backup salt" }
            require(nonce.size == NONCE_LENGTH_BYTES) { "Invalid backup nonce" }
            require(ciphertext.size > TAG_LENGTH_BITS / 8) { "Invalid backup ciphertext" }
            val key = deriveKey(password, salt, envelope.iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, nonce))
            cipher.updateAAD(envelopeAad())
            val plaintext = cipher.doFinal(ciphertext).toString(StandardCharsets.UTF_8)
            decode(plaintext)
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid password or encrypted backup", error)
        }
    }

    private fun buildBackup(cards: List<ClipboardCard>): VaultBackupFile = VaultBackupFile(
        cards = cards.map { card ->
            VaultBackupCard(
                content = card.content,
                createdAtMillis = card.createdAtMillis,
                pinned = card.pinned,
                isSensitive = card.isSensitive
            )
        }
    )

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        return try {
            val secret = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
            SecretKeySpec(secret.encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun envelopeAad(): ByteArray = "$ENCRYPTED_BACKUP_FORMAT:$ENCRYPTED_BACKUP_VERSION:AES-256-GCM:PBKDF2WithHmacSHA256".toByteArray(StandardCharsets.UTF_8)
}
