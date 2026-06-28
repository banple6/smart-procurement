package com.example.data

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PasswordHashResult(val salt: String, val hash: String)

object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256
    private val secureRandom = SecureRandom()

    fun hash(password: String): PasswordHashResult {
        val saltBytes = ByteArray(16)
        secureRandom.nextBytes(saltBytes)
        val hashBytes = pbkdf2(password, saltBytes)
        return PasswordHashResult(
            salt = saltBytes.toHex(),
            hash = hashBytes.toHex()
        )
    }

    fun verify(password: String, salt: String, expectedHash: String): Boolean {
        val saltBytes = salt.hexToBytes()
        val hashBytes = pbkdf2(password, saltBytes)
        val actualHash = hashBytes.toHex()
        return actualHash == expectedHash
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hex length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
