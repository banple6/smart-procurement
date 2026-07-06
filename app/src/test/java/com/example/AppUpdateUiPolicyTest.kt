package com.smartprocurement.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppUpdateUiPolicyTest {
    @Test
    fun profile_exposes_about_update_entry() {
        val screens = File("src/main/java/com/example/ui/Screens.kt").readText()
        val app = File("src/main/java/com/example/ui/SupplyApp.kt").readText()
        val viewModel = File("src/main/java/com/example/ui/SupplyViewModel.kt").readText()

        assertTrue(screens.contains("关于与更新"))
        assertTrue(app.contains("AboutUpdateScreen"))
        assertTrue(viewModel.contains("object AboutUpdate"))
    }

    @Test
    fun update_install_uses_system_confirmation_without_broad_storage_permission() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val service = File("src/main/java/com/example/data/AppUpdateInstaller.kt").readText()

        assertFalse(manifest.contains("REQUEST_INSTALL_PACKAGES"))
        assertFalse(manifest.contains("WRITE_EXTERNAL_STORAGE"))
        assertTrue(service.contains("ACTION_VIEW"))
        assertTrue(service.contains("application/vnd.android.package-archive"))
    }
}
