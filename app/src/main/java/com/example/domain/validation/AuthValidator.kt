package com.smartprocurement.internal.domain.validation

data class LoginInput(
    val username: String,
    val password: String
)

data class PasswordChangeInput(
    val username: String,
    val oldPassword: String,
    val newPassword: String,
    val confirmPassword: String
)

data class ValidationResult(val errors: Map<String, String>) {
    val isValid: Boolean = errors.isEmpty()
}

object AuthValidator {
    fun validateLogin(input: LoginInput): ValidationResult {
        val errors = linkedMapOf<String, String>()
        if (input.username.isBlank()) errors["username"] = "账号不能为空"
        if (input.password.isBlank()) errors["password"] = "密码不能为空"
        return ValidationResult(errors)
    }

    fun validatePasswordChange(input: PasswordChangeInput): ValidationResult {
        val errors = linkedMapOf<String, String>()
        if (input.oldPassword.isBlank()) errors["oldPassword"] = "原密码不能为空"
        if (input.newPassword != input.confirmPassword) {
            errors["confirmPassword"] = "两次输入的新密码不一致"
        }
        if (input.newPassword.equals(input.oldPassword)) {
            errors["newPassword"] = "新密码不能与原密码相同"
        } else if (input.newPassword.equals(input.username, ignoreCase = true)) {
            errors["newPassword"] = "新密码不能与账号相同"
        } else {
            val hasLetter = input.newPassword.any { it.isLetter() }
            val hasDigit = input.newPassword.any { it.isDigit() }
            if (input.newPassword.length < 8 || !hasLetter || !hasDigit) {
                errors["newPassword"] = "密码至少 8 位，且包含字母和数字"
            }
        }
        return ValidationResult(errors)
    }
}
