package com.smartprocurement.internal

import com.smartprocurement.internal.data.PasswordHasher
import com.smartprocurement.internal.domain.validation.AuthValidator
import com.smartprocurement.internal.domain.validation.LoginInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {
    @Test
    fun login_requires_username_and_password() {
        val result = AuthValidator.validateLogin(LoginInput("", ""))

        assertFalse(result.isValid)
        assertEquals("账号不能为空", result.errors["username"])
        assertEquals("密码不能为空", result.errors["password"])
    }

    @Test
    fun password_hash_uses_random_salt_and_verifies_original_password() {
        val first = PasswordHasher.hash("secure123")
        val second = PasswordHasher.hash("secure123")

        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.hash, second.hash)
        assertTrue(PasswordHasher.verify("secure123", first.salt, first.hash))
        assertFalse(PasswordHasher.verify("wrong", first.salt, first.hash))
    }
}
