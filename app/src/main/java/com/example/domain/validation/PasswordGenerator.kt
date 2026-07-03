package com.smartprocurement.internal.domain.validation

import java.security.SecureRandom

object PasswordGenerator {
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val DIGITS = "23456789"
    private const val MIN_LENGTH = 10
    private val random = SecureRandom()

    fun generate(username: String, length: Int = MIN_LENGTH): String {
        val targetLength = maxOf(length, MIN_LENGTH)
        repeat(100) {
            val chars = mutableListOf(
                UPPER.randomChar(),
                LOWER.randomChar(),
                DIGITS.randomChar()
            )
            val pool = UPPER + LOWER + DIGITS
            while (chars.size < targetLength) chars += pool.randomChar()
            chars.shuffle(random)
            val password = chars.joinToString("")
            if (!password.contains(username, ignoreCase = true)) return password
        }
        return "A7b" + (1..(targetLength - 3)).joinToString("") { DIGITS.randomChar().toString() }
    }

    private fun String.randomChar(): Char = this[random.nextInt(length)]
}
