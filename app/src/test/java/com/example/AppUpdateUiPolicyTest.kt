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

    @Test
    fun login_page_can_show_mandatory_update_before_authentication() {
        val viewModel = File("src/main/java/com/example/ui/SupplyViewModel.kt").readText()
        val app = File("src/main/java/com/example/ui/SupplyApp.kt").readText()

        assertTrue(viewModel.contains("checkForAppUpdate(showNoUpdate = false)"))
        assertFalse(viewModel.contains("fun checkForAppUpdate(showNoUpdate: Boolean = false) {\n        if (authToken.isBlank()) return"))
        assertFalse(viewModel.contains("if (authToken.isBlank() || isDownloadingAppUpdate) return"))
        assertTrue(app.contains("必须更新"))
        assertTrue(app.contains("立即更新"))
        assertTrue(app.contains("onDismissRequest = {}"))
    }

    @Test
    fun startup_update_check_runs_after_update_state_is_initialized() {
        val viewModel = File("src/main/java/com/example/ui/SupplyViewModel.kt").readText()

        val updateStateIndex = viewModel.indexOf("var isCheckingAppUpdate by mutableStateOf(false)")
        val startupCheckIndex = viewModel.indexOf("checkForAppUpdate(showNoUpdate = false)")

        assertTrue(updateStateIndex >= 0)
        assertTrue(startupCheckIndex > updateStateIndex)
    }

    @Test
    fun product_sync_clears_stale_local_products_when_server_returns_empty_list() {
        val repository = File("src/main/java/com/example/data/SupplyRepository.kt").readText()
        val replaceProductsStart = repository.indexOf("suspend fun replaceProducts(products: List<ProductEntity>)")
        val replaceOrdersStart = repository.indexOf("suspend fun saveProduct", replaceProductsStart)
        val replaceProductsBody = repository.substring(replaceProductsStart, replaceOrdersStart)

        val clearIndex = replaceProductsBody.indexOf("supplyDao.clearProducts()")
        val insertIndex = replaceProductsBody.indexOf("supplyDao.insertProducts(products)")

        assertTrue(clearIndex >= 0)
        assertTrue(insertIndex > clearIndex)
    }

    @Test
    fun app_update_apk_cache_directory_is_exposed_to_file_provider() {
        val installer = File("src/main/java/com/example/data/AppUpdateInstaller.kt").readText()
        val fileProviderPaths = File("src/main/res/xml/file_paths.xml").readText()

        assertTrue(installer.contains("app_updates/"))
        assertTrue(fileProviderPaths.contains("cache-path"))
        assertTrue(fileProviderPaths.contains("app_updates"))
    }

    @Test
    fun file_provider_update_install_failure_is_not_reported_as_network_failure() {
        val viewModel = File("src/main/java/com/example/ui/SupplyViewModel.kt").readText()

        val fileProviderIndex = viewModel.indexOf("configured root")
        val networkFailureIndex = viewModel.indexOf("网络连接失败，请稍后重试")

        assertTrue(fileProviderIndex >= 0)
        assertTrue(fileProviderIndex < networkFailureIndex)
    }
}
