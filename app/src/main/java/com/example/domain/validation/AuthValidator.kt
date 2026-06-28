package com.smartprocurement.internal.domain.validation

data class LoginInput(
    val username: String,
    val password: String
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
}
