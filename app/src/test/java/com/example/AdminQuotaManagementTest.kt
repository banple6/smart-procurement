package com.smartprocurement.internal

import com.smartprocurement.internal.data.UnitQuota
import com.smartprocurement.internal.data.ApiRequestException
import com.smartprocurement.internal.ui.QuotaMutationFailure
import com.smartprocurement.internal.ui.Screen
import com.smartprocurement.internal.ui.canOpenScreen
import com.smartprocurement.internal.ui.classifyQuotaMutationFailure
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
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class AdminQuotaManagementTest {
    @Test
    fun admin_quota_list_parses_server_authoritative_values() {
        val quota = UnitQuota.fromJson(
            JSONObject()
                .put("enabled", true)
                .put("quota_month", "2026-08")
                .put("default_monthly_quota_cents", 2500050)
                .put("base_quota_cents", 2500050)
                .put("effective_quota_cents", 2600050)
                .put("monthly_correction_cents", 100000)
                .put("available_cents", 1900040)
                .put("used_this_month_cents", 600010)
                .put("version", 7)
                .put("updated_at", "2026-08-27 17:35:08")
                .put("display_updated_at", "2026-08-28 01:35:08")
                .put("future_months", org.json.JSONArray().put(JSONObject()
                    .put("quota_month", "2026-09")
                    .put("planned_quota_cents", 2500050)
                    .put("source", "explicit")
                    .put("editable", true)))
        )

        assertTrue(quota.enabled)
        assertEquals(2500050L, quota.defaultMonthlyQuotaCents)
        assertEquals(1900040L, quota.availableCents)
        assertEquals(600010L, quota.usedThisMonthCents)
        assertEquals(2600050L, quota.effectiveQuotaCents)
        assertEquals(100000L, quota.monthlyCorrectionCents)
        assertEquals("2026-09", quota.futureMonths.single().quotaMonth)
        assertTrue(quota.futureMonths.single().editable)
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
        assertTrue(viewModel.contains("correctAdminCurrentMonthQuota"))
        assertTrue(viewModel.contains("saveAdminFutureQuotaPlan"))
        assertTrue(viewModel.contains("restoreAdminFutureQuotaDefault"))
        assertTrue(viewModel.contains("STALE_WRITE"))
        assertTrue(viewModel.contains("额度信息已更新，请重新确认。"))
        assertTrue(viewModel.contains("请求结果未知，已刷新额度信息，请确认后再操作。"))
        assertTrue(viewModel.contains("screen is Screen.UnitQuotaManagement"))
        assertTrue(viewModel.contains("screen is Screen.UnitQuotaDetail"))
        assertTrue(quotaScreen.contains("PullToRefreshBox"))
        assertTrue(quotaScreen.contains("额度变动记录"))
    }

    @Test
    fun quota_mutation_classification_keeps_stale_business_and_network_outcomes_distinct() {
        assertEquals(QuotaMutationFailure.STALE, classifyQuotaMutationFailure(ApiRequestException(409, "STALE_WRITE", "版本已过期")))
        assertEquals(QuotaMutationFailure.BUSINESS, classifyQuotaMutationFailure(ApiRequestException(409, message = "减少额度不能使当前可用余额小于 0")))
        assertEquals(QuotaMutationFailure.NETWORK_RESULT_UNKNOWN, classifyQuotaMutationFailure(IOException("timeout")))
    }

    @Test
    fun cached_quota_is_warning_only_and_does_not_disable_authoritative_order_submission() {
        val cart = File("src/main/java/com/example/ui/CartAndOrder.kt").readText()
        assertTrue(cart.contains("val quotaExceeded = quota.enabled && totalCents > quota.availableCents"))
        assertTrue(cart.contains("Cached quota is advisory only"))
        assertFalse(cart.contains("enabled = !viewModel.isSubmittingOrder && !quotaExceeded && staleLines.isEmpty()"))
    }

    @Test
    fun disabled_quota_is_not_auto_enabled_by_the_client() {
        val quota = UnitQuota()
        assertFalse(quota.enabled)
    }
}
