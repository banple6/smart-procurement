package com.smartprocurement.internal

import com.smartprocurement.internal.data.UnitQuota
import com.smartprocurement.internal.ui.Screen
import com.smartprocurement.internal.ui.canOpenScreen
import com.smartprocurement.internal.ui.quotaAmountToCents
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AdminQuotaManagementTest {
    @Test
    fun admin_quota_list_parses_server_authoritative_values() {
        val quota = UnitQuota.fromJson(
            JSONObject()
                .put("enabled", true)
                .put("quota_month", "2026-08")
                .put("default_monthly_quota_cents", 2500050)
                .put("available_cents", 1900040)
                .put("used_this_month_cents", 600010)
                .put("version", 7)
                .put("updated_at", "2026-08-27 17:35:08")
                .put("display_updated_at", "2026-08-28 01:35:08")
        )

        assertTrue(quota.enabled)
        assertEquals(2500050L, quota.defaultMonthlyQuotaCents)
        assertEquals(1900040L, quota.availableCents)
        assertEquals(600010L, quota.usedThisMonthCents)
        assertEquals(7, quota.version)
        assertEquals("2026-08-28 01:35:08", quota.updatedAt)
    }

    @Test
    fun quota_money_input_keeps_cents_exact_and_rejects_invalid_values() {
        assertEquals(2500050L, quotaAmountToCents("25000.50"))
        assertEquals(0L, quotaAmountToCents("0"))
        assertNull(quotaAmountToCents("12.345"))
        assertNull(quotaAmountToCents("-10"))
        assertNull(quotaAmountToCents("abc"))
    }

    @Test
    fun quota_management_is_admin_only() {
        assertTrue(canOpenScreen("admin", Screen.UnitQuotaManagement))
        assertTrue(canOpenScreen("admin", Screen.UnitQuotaDetail("unit-1")))
        assertFalse(canOpenScreen("unit_user", Screen.UnitQuotaManagement))
        assertFalse(canOpenScreen("unit_user", Screen.UnitQuotaDetail("unit-1")))
    }

    @Test
    fun quota_ui_uses_server_write_and_stale_refresh_policy() {
        val viewModel = File("src/main/java/com/example/ui/SupplyViewModel.kt").readText()
        val api = File("src/main/java/com/example/data/ProcurementApiClient.kt").readText()
        val screens = File("src/main/java/com/example/ui/Screens.kt").readText()
        val quotaScreen = File("src/main/java/com/example/ui/AdminQuotaScreens.kt").readText()

        assertTrue(screens.contains("单位采购额度"))
        assertTrue(api.contains("expected_version"))
        assertTrue(viewModel.contains("saveAdminQuotaSettings"))
        assertTrue(viewModel.contains("adjustAdminQuota"))
        assertTrue(viewModel.contains("STALE_WRITE"))
        assertTrue(viewModel.contains("数据已被其他管理员更新，已刷新最新状态。"))
        assertTrue(viewModel.contains("screen is Screen.UnitQuotaManagement"))
        assertTrue(viewModel.contains("screen is Screen.UnitQuotaDetail"))
        assertTrue(quotaScreen.contains("PullToRefreshBox"))
        assertTrue(quotaScreen.contains("额度变动记录"))
    }

    @Test
    fun disabled_quota_is_not_auto_enabled_by_the_client() {
        val quota = UnitQuota()
        assertFalse(quota.enabled)
    }
}
