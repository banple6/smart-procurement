package com.smartprocurement.internal

import com.smartprocurement.internal.data.ApiRequestException
import com.smartprocurement.internal.data.ProcurementApiClient
import com.smartprocurement.internal.ui.Screen
import com.smartprocurement.internal.ui.SessionCapabilities
import com.smartprocurement.internal.ui.authorizationFailureMessage
import com.smartprocurement.internal.ui.canOpenScreen
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthorizationCapabilityAlignmentTest {
    @Test
    fun auth_me_capabilities_are_parsed_and_limit_admin_entries() {
        val client = ProcurementApiClient("http://127.0.0.1/api/v1/")
        val user = client.parseUser(
            JSONObject()
                .put("id", "admin-1")
                .put("username", "limited_admin")
                .put("display_name", "受限管理员")
                .put("role", "admin")
                .put("can_manage_accounts", false)
                .put("can_view_system_status", true)
                .put("can_manage_backups", true)
        )
        val capabilities = SessionCapabilities.from(
            role = user.role,
            canManageAccounts = user.canManageAccounts,
            canViewSystemStatus = user.canViewSystemStatus,
            canManageBackups = user.canManageBackups
        )

        assertFalse(capabilities.canManageAccounts)
        assertTrue(capabilities.canViewSystemStatus)
        assertTrue(capabilities.canManageBackups)
        assertTrue(canOpenScreen(user.role, Screen.SystemStatus, capabilities))
        assertFalse(canOpenScreen(user.role, Screen.AccountManagement, capabilities))
    }

    @Test
    fun unit_and_unknown_roles_never_gain_admin_capabilities_or_screens() {
        val unitCapabilities = SessionCapabilities.from(
            role = "unit_user",
            canManageAccounts = true,
            canViewSystemStatus = true,
            canManageBackups = true
        )
        val unknownCapabilities = SessionCapabilities.from(
            role = "auditor",
            canManageAccounts = true,
            canViewSystemStatus = true,
            canManageBackups = true
        )

        assertFalse(unitCapabilities.isAdmin)
        assertFalse(unitCapabilities.canViewSystemStatus)
        assertFalse(unknownCapabilities.isAdmin)
        assertFalse(unknownCapabilities.canManageBackups)
        assertFalse(canOpenScreen("unit_user", Screen.Outbounds, unitCapabilities))
        assertFalse(canOpenScreen("auditor", Screen.SystemStatus, unknownCapabilities))
        assertTrue(canOpenScreen("auditor", Screen.Home, unknownCapabilities))
    }

    @Test
    fun a_new_session_replaces_old_admin_capabilities() {
        val adminCapabilities = SessionCapabilities.from(
            role = "admin",
            canManageAccounts = true,
            canViewSystemStatus = true
        )
        val unitCapabilities = SessionCapabilities.from(role = "unit_user")

        assertTrue(canOpenScreen("admin", Screen.AccountManagement, adminCapabilities))
        assertTrue(canOpenScreen("admin", Screen.SystemStatus, adminCapabilities))
        assertFalse(canOpenScreen("unit_user", Screen.AccountManagement, unitCapabilities))
        assertFalse(canOpenScreen("unit_user", Screen.SystemStatus, unitCapabilities))
    }

    @Test
    fun authentication_expiry_and_forbidden_remain_distinct() {
        val expired = ApiRequestException(401, message = "登录状态已失效，请重新登录")
        val forbidden = ApiRequestException(403, message = "当前账号无管理员权限")

        assertEquals("登录已过期，请重新登录", authorizationFailureMessage(expired))
        assertEquals("当前账号无管理员权限", authorizationFailureMessage(forbidden))
    }
}
