package com.tailsync.app.crypto

import android.util.Base64
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Fernet-compatible encryption/decryption utility.
 * 
 * Compatible with Python's cryptography.fernet.Fernet implementation.
 * Uses PBKDF2-HMAC-SHA256 for key derivation with a shared static salt.
 * 
 * Fernet token format:
 * - Version (1 byte): 0x80
 * - Timestamp (8 bytes): big-endian seconds since epoch
 * - IV (16 bytes): random
 * - Ciphertext (variable): AES-128-CBC with PKCS7 padding
 * - HMAC (32 bytes): SHA256 over version+timestamp+IV+ciphertext
 */
object FernetCrypto {
    
    private const val FERNET_VERSION: Byte = 0x80.toByte()
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BYTES = 32
    private const val IV_LENGTH = 16
    private const val HMAC_LENGTH = 32
    
    // Static salt - MUST match Python client exactly
    private val STATIC_SALT = "TailSync_Shared_Salt_v1".toByteArray(Charsets.UTF_8)
    
    private val secureRandom = SecureRandom()
    
    /**
     * Derives a 32-byte key from password using PBKDF2-HMAC-SHA256.
     * Returns the raw key bytes (not Base64 encoded).
     */
    fun deriveKey(password: String): ByteArray {
        if (password.isEmpty()) {
            throw IllegalArgumentException("Password cannot be empty")
        }
        
        val spec = PBEKeySpec(
            password.toCharArray(),
            STATIC_SALT,
            PBKDF2_ITERATIONS,
            KEY_LENGTH_BYTES * 8  // bits
        )
        
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
    
    /**
     * Encrypts plaintext using Fernet format.
     * 
     * @param plaintext The text to encrypt (UTF-8)
     * @param key 32-byte key from deriveKey()
     * @return URL-safe Base64 encoded Fernet token
     */
    fun encrypt(plaintext: String, key: ByteArray): String {
        require(key.size == KEY_LENGTH_BYTES) { "Key must be $KEY_LENGTH_BYTES bytes" }
        
        // Split key per Fernet spec: first 16 bytes = signing, last 16 bytes = encryption
        val signingKey = key.copyOfRange(0, 16)
        val encryptionKey = key.copyOfRange(16, 32)
        
        // Generate random IV
        val iv = ByteArray(IV_LENGTH)
        secureRandom.nextBytes(iv)
        
        // Current timestamp (seconds since epoch)
        val timestamp = System.currentTimeMillis() / 1000
        
        // Encrypt with AES-128-CBC
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encryptionKey, "AES"), IvParameterSpec(iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        
        // Build token: version (1) + timestamp (8) + IV (16) + ciphertext (variable)
        val tokenWithoutHmac = ByteBuffer.allocate(1 + 8 + IV_LENGTH + ciphertext.size)
            .put(FERNET_VERSION)
            .putLong(timestamp)
            .put(iv)
            .put(ciphertext)
            .array()
        
        // Compute HMAC-SHA256 over version+timestamp+IV+ciphertext
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(signingKey, "HmacSHA256"))
        val hmac = mac.doFinal(tokenWithoutHmac)
        
        // Final token: tokenWithoutHmac + HMAC
        val fullToken = ByteBuffer.allocate(tokenWithoutHmac.size + HMAC_LENGTH)
            .put(tokenWithoutHmac)
            .put(hmac)
            .array()
        
        // URL-safe Base64 encode WITH padding (to match Python Fernet format)
        return Base64.encodeToString(fullToken, Base64.URL_SAFE or Base64.NO_WRAP)
    }
    
    /**
     * Decrypts a Fernet token.
     * 
     * @param token URL-safe Base64 encoded Fernet token
     * @param key 32-byte key from deriveKey()
     * @return Decrypted plaintext (UTF-8)
     * @throws IllegalArgumentException if token is invalid or HMAC verification fails
     */
    fun decrypt(token: String, key: ByteArray): String {
        require(key.size == KEY_LENGTH_BYTES) { "Key must be $KEY_LENGTH_BYTES bytes" }
        
        // Normalize Base64: handle both padded and unpadded input
        // Python Fernet uses padding, but Android might receive tokens with or without
        val normalizedToken = normalizeBase64(token)
        
        // URL-safe Base64 decode (DEFAULT handles padding automatically)
        val tokenBytes = try {
            Base64.decode(normalizedToken, Base64.URL_SAFE or Base64.NO_WRAP)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid Base64 token", e)
        }
        
        // Minimum token size: version(1) + timestamp(8) + IV(16) + ciphertext(16 min) + HMAC(32)
        if (tokenBytes.size < 1 + 8 + IV_LENGTH + 16 + HMAC_LENGTH) {
            throw IllegalArgumentException("Token too short")
        }
        
        // Split key per Fernet spec
        val signingKey = key.copyOfRange(0, 16)
        val encryptionKey = key.copyOfRange(16, 32)
        
        val buffer = ByteBuffer.wrap(tokenBytes)
        
        // Verify version
        val version = buffer.get()
        if (version != FERNET_VERSION) {
            throw IllegalArgumentException("Invalid Fernet version: $version")
        }
        
        // Read timestamp (we don't enforce TTL for clipboard sync)
        val timestamp = buffer.getLong()
        
        // Read IV
        val iv = ByteArray(IV_LENGTH)
        buffer.get(iv)
        
        // Read ciphertext (everything except last 32 bytes which is HMAC)
        val ciphertextLength = tokenBytes.size - (1 + 8 + IV_LENGTH + HMAC_LENGTH)
        val ciphertext = ByteArray(ciphertextLength)
        buffer.get(ciphertext)
        
        // Read HMAC
        val providedHmac = ByteArray(HMAC_LENGTH)
        buffer.get(providedHmac)
        
        // Verify HMAC
        val tokenWithoutHmac = tokenBytes.copyOfRange(0, tokenBytes.size - HMAC_LENGTH)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(signingKey, "HmacSHA256"))
        val computedHmac = mac.doFinal(tokenWithoutHmac)
        
        if (!computedHmac.contentEquals(providedHmac)) {
            throw IllegalArgumentException("HMAC verification failed - wrong password or corrupted data")
        }
        
        // Decrypt with AES-128-CBC
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encryptionKey, "AES"), IvParameterSpec(iv))
        val plaintext = cipher.doFinal(ciphertext)
        
        return String(plaintext, Charsets.UTF_8)
    }
    
    /**
     * Checks if a string looks like a Fernet token.
     * Used to determine if decryption should be attempted.
     */
    fun looksLikeFernetToken(text: String): Boolean {
        if (text.isBlank()) return false
        
        return try {
            // Normalize and decode to check
            val normalized = normalizeBase64(text)
            val bytes = Base64.decode(normalized, Base64.URL_SAFE or Base64.NO_WRAP)
            // Check minimum length and version byte
            bytes.size >= 1 + 8 + IV_LENGTH + 16 + HMAC_LENGTH && bytes[0] == FERNET_VERSION
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Normalize Base64 string to ensure proper padding.
     * Handles both padded (Python) and unpadded (some Android) tokens.
     */
    private fun normalizeBase64(input: String): String {
        val trimmed = input.trim()
        // Calculate required padding
        val paddingNeeded = (4 - (trimmed.length % 4)) % 4
        return if (paddingNeeded > 0 && !trimmed.endsWith("=")) {
            trimmed + "=".repeat(paddingNeeded)
        } else {
            trimmed
        }
    }
}
