package com.smartprocurement.internal

import com.smartprocurement.internal.data.PasswordHasher
import com.smartprocurement.internal.domain.validation.AuthValidator
import com.smartprocurement.internal.domain.validation.PasswordChangeInput
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
    fun password_change_requires_letters_digits_length_and_not_username() {
        val weak = AuthValidator.validatePasswordChange(
            PasswordChangeInput(username = "unit001", oldPassword = "OldPass123", newPassword = "12345678", confirmPassword = "12345678")
        )
        assertFalse(weak.isValid)
        assertEquals("密码至少 8 位，且包含字母和数字", weak.errors["newPassword"])

        val same = AuthValidator.validatePasswordChange(
            PasswordChangeInput(username = "unit001", oldPassword = "OldPass123", newPassword = "unit001", confirmPassword = "unit001")
        )
        assertFalse(same.isValid)
        assertEquals("新密码不能与账号相同", same.errors["newPassword"])

        val sameAsOld = AuthValidator.validatePasswordChange(
            PasswordChangeInput(username = "unit001", oldPassword = "OldPass123", newPassword = "OldPass123", confirmPassword = "OldPass123")
        )
        assertFalse(sameAsOld.isValid)
        assertEquals("新密码不能与原密码相同", sameAsOld.errors["newPassword"])

        val mismatch = AuthValidator.validatePasswordChange(
            PasswordChangeInput(username = "unit001", oldPassword = "OldPass123", newPassword = "NewPass123", confirmPassword = "NewPass124")
        )
        assertFalse(mismatch.isValid)
        assertEquals("两次输入的新密码不一致", mismatch.errors["confirmPassword"])

        val valid = AuthValidator.validatePasswordChange(
            PasswordChangeInput(username = "unit001", oldPassword = "OldPass123", newPassword = "NewPass123", confirmPassword = "NewPass123")
        )
        assertTrue(valid.errors.toString(), valid.isValid)
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
