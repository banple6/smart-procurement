package com.example

import com.example.data.PasswordHasher
import com.example.domain.validation.ActivationInput
import com.example.domain.validation.AuthValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidationTest {
    @Test
    fun activation_rejects_invalid_phone_and_mismatched_passwords() {
        val result = AuthValidator.validateActivation(
            ActivationInput(
                inviteCode = "LOGI-2026",
                realName = "张伟",
                phone = "12345",
                department = "后勤管理处",
                username = "zhangwei",
                password = "abc12345",
                confirmPassword = "abc123456"
            ),
            expectedInviteCode = "LOGI-2026"
        )

        assertFalse(result.isValid)
        assertTrue(result.errors["phone"] == "手机号格式不正确")
        assertTrue(result.errors["confirmPassword"] == "两次密码必须一致")
    }

    @Test
    fun activation_accepts_complete_internal_account() {
        val result = AuthValidator.validateActivation(
            ActivationInput(
                inviteCode = "LOGI-2026",
                realName = "李华",
                phone = "13812345678",
                department = "仓储配送部",
                username = "lihua",
                password = "secure123",
                confirmPassword = "secure123"
            ),
            expectedInviteCode = "LOGI-2026"
        )

        assertTrue(result.errors.toString(), result.isValid)
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
