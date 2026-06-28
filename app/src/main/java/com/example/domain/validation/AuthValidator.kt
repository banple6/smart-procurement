package com.example.domain.validation

data class ActivationInput(
    val inviteCode: String,
    val realName: String,
    val phone: String,
    val department: String,
    val username: String,
    val password: String,
    val confirmPassword: String
)

data class LoginInput(
    val username: String,
    val password: String
)

data class ValidationResult(val errors: Map<String, String>) {
    val isValid: Boolean = errors.isEmpty()
}

object AuthValidator {
    private val phoneRegex = Regex("^1[3-9]\\d{9}$")

    fun validateLogin(input: LoginInput): ValidationResult {
        val errors = linkedMapOf<String, String>()
        if (input.username.isBlank()) errors["username"] = "账号不能为空"
        if (input.password.isBlank()) errors["password"] = "密码不能为空"
        return ValidationResult(errors)
    }

    fun validateActivation(input: ActivationInput, expectedInviteCode: String): ValidationResult {
        val errors = linkedMapOf<String, String>()
        if (input.inviteCode.isBlank() || input.inviteCode != expectedInviteCode) errors["inviteCode"] = "邀请码无效"
        if (input.realName.isBlank()) errors["realName"] = "姓名不能为空"
        if (!phoneRegex.matches(input.phone)) errors["phone"] = "手机号格式不正确"
        if (input.department.isBlank()) errors["department"] = "所属部门不能为空"
        if (input.username.isBlank()) errors["username"] = "账号不能为空"
        if (input.password.isBlank()) errors["password"] = "密码不能为空"
        if (input.password != input.confirmPassword) errors["confirmPassword"] = "两次密码必须一致"
        return ValidationResult(errors)
    }
}
