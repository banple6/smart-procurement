package com.smartprocurement.internal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SystemStatusEntryPolicyTest {
    @Test
    fun admin_profile_exposes_system_status_entry() {
        val screens = File("src/main/java/com/example/ui/Screens.kt").readText()
        val app = File("src/main/java/com/example/ui/SupplyApp.kt").readText()

        assertTrue(screens.contains("系统状态"))
        assertTrue(screens.contains("Screen.SystemStatus"))
        assertTrue(app.contains("SystemStatusScreen"))
    }
}
