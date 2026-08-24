package com.smartprocurement.internal

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidBuildVariantPolicyTest {
    private val root = File(System.getProperty("user.dir"))
    private val buildScript = File(root, "build.gradle.kts").readText()

    @Test
    fun release_is_fail_closed_for_transport_signing_and_push() {
        assertTrue(buildScript.contains("PROD_API_BASE_URL"))
        assertTrue(buildScript.contains("PROD_API_BASE_URL is required and must use https://"))
        assertTrue(buildScript.contains("signingConfigs.getByName(\"release\")"))
        assertTrue(buildScript.contains("debug signing is not allowed"))
        assertTrue(buildScript.contains("Release build requires JPUSH_APP_KEY"))
        assertFalse(buildScript.contains("ALLOW_INSECURE_HTTP_RELEASE"))
    }

    @Test
    fun staging_requires_an_explicit_test_endpoint_and_only_allows_loopback_cleartext() {
        assertTrue(buildScript.contains("STAGING_API_BASE_URL"))
        assertTrue(buildScript.contains("STAGING_API_BASE_URL is required and must use HTTPS or an ADB/emulator loopback URL"))
        assertTrue(buildScript.contains("applicationIdSuffix = \".staging\""))
        assertTrue(buildScript.contains("三公鲜配（测试）"))
        assertFalse(buildScript.contains("http://47.94.227.58"))

        val stagingConfig = File(root, "src/staging/res/xml/network_security_config.xml").readText()
        assertTrue(stagingConfig.contains("cleartextTrafficPermitted=\"false\""))
        assertTrue(stagingConfig.contains("127.0.0.1"))
        assertFalse(stagingConfig.contains("47.94.227.58"))
    }

    @Test
    fun local_variant_is_loopback_only_and_clearly_labelled() {
        assertTrue(buildScript.contains("LOCAL_API_BASE_URL"))
        assertTrue(buildScript.contains("127\\\\.0\\\\.0\\\\.1|localhost|10\\\\.0\\\\.2\\\\.2"))
        assertTrue(buildScript.contains("applicationIdSuffix = \".local\""))
        assertTrue(buildScript.contains("三公鲜配（本地测试）"))

        val manifest = File(root, "src/main/AndroidManifest.xml").readText()
        val secureConfig = File(root, "src/main/res/xml/network_security_config.xml").readText()
        val localConfig = File(root, "src/local/res/xml/network_security_config.xml").readText()

        assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        assertTrue(secureConfig.contains("cleartextTrafficPermitted=\"false\""))
        assertTrue(localConfig.contains("cleartextTrafficPermitted=\"true\""))
        assertTrue(localConfig.contains("127.0.0.1"))
    }
}
