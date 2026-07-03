package com.smartprocurement.internal

import com.smartprocurement.internal.domain.validation.PasswordGenerator
import com.smartprocurement.internal.ui.Screen
import com.smartprocurement.internal.ui.canOpenScreen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSecurityUiPolicyTest {
    @Test
    fun generated_initial_password_is_strong_and_avoids_confusing_characters() {
        repeat(20) {
            val password = PasswordGenerator.generate("st001")
            assertTrue(password.length >= 10)
            assertTrue(password.any { it.isUpperCase() })
            assertTrue(password.any { it.isLowerCase() })
            assertTrue(password.any { it.isDigit() })
            assertFalse(password.contains("st001", ignoreCase = true))
            assertFalse(password.any { it in "O0Il" })
        }
    }

    @Test
    fun unit_user_cannot_open_admin_only_screens() {
        assertFalse(canOpenScreen("unit_user", Screen.UnitManagement))
        assertFalse(canOpenScreen("unit_user", Screen.AccountManagement))
        assertFalse(canOpenScreen("unit_user", Screen.Ledger))
        assertFalse(canOpenScreen("unit_user", Screen.InventoryRecords))
        assertFalse(canOpenScreen("unit_user", Screen.AddProduct))
        assertFalse(canOpenScreen("unit_user", Screen.EditProduct("p1")))
        assertFalse(canOpenScreen("unit_user", Screen.ShippingProof("o1")))
        assertTrue(canOpenScreen("unit_user", Screen.Home))
        assertTrue(canOpenScreen("admin", Screen.AccountManagement))
    }
}
