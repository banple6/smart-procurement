package com.smartprocurement.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OnboardingInvitePolicyTest {
    private val root = File(System.getProperty("user.dir"))

    @Test
    fun onboarding_chooses_entry_method_not_real_role() {
        val authScreens = File(root, "src/main/java/com/example/ui/AuthScreens.kt").readText()
        val viewModel = File(root, "src/main/java/com/example/ui/SupplyViewModel.kt").readText()
        val sessionStore = File(root, "src/main/java/com/example/data/SessionStore.kt").readText()

        assertTrue(authScreens.contains("请选择进入方式"))
        assertTrue(authScreens.contains("已有账号登录"))
        assertTrue(authScreens.contains("扫描邀请二维码"))
        assertTrue(authScreens.contains("输入邀请码"))
        assertFalse(authScreens.contains("请选择使用身份"))
        assertFalse(authScreens.contains("进入管理者入口"))
        assertFalse(authScreens.contains("进入子单位入口"))
        assertFalse(authScreens.contains("没有账号？使用单位邀请码注册"))
        assertFalse(authScreens.contains("使用管理者邀请码注册"))

        assertTrue(viewModel.contains("preferred_entry_method"))
        assertFalse(sessionStore.contains("selected_onboarding_path"))
    }

    @Test
    fun invite_registration_handles_manager_pending_approval_without_session() {
        val apiClient = File(root, "src/main/java/com/example/data/ProcurementApiClient.kt").readText()
        val viewModel = File(root, "src/main/java/com/example/ui/SupplyViewModel.kt").readText()
        val extraScreens = File(root, "src/main/java/com/example/ui/ExtraScreens.kt").readText()

        assertTrue(apiClient.contains("display_role"))
        assertTrue(apiClient.contains("issuer_name_masked"))
        assertTrue(apiClient.contains("pending_approval"))
        assertTrue(viewModel.contains("管理者申请已提交"))
        assertTrue(viewModel.contains("pendingApproval"))
        assertTrue(extraScreens.contains("管理者权限需要系统管理员审批"))
        assertFalse(extraScreens.contains("提交注册"))
    }

    @Test
    fun invite_qr_scheme_does_not_encode_role_or_unit() {
        val apiClient = File(root, "src/main/java/com/example/data/ProcurementApiClient.kt").readText()
        val extraScreens = File(root, "src/main/java/com/example/ui/ExtraScreens.kt").readText()

        assertTrue(apiClient.contains("jingrongxianpei://invite?token="))
        assertTrue(extraScreens.contains("extractInviteToken"))
        assertFalse(extraScreens.contains("role="))
        assertFalse(extraScreens.contains("unit_id="))
        assertFalse(extraScreens.contains("is_admin"))
    }
}
