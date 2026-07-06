package com.smartprocurement.internal

import com.smartprocurement.internal.ui.extractWebLoginToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WebLoginQrPolicyTest {
    @Test
    fun extracts_only_jingrong_web_login_tokens() {
        assertEquals("abc123", extractWebLoginToken("jingrongxianpei://web-login?token=abc123"))
        assertEquals("abc 123", extractWebLoginToken("jingrongxianpei://web-login?token=abc%20123"))
        assertEquals("", extractWebLoginToken("https://example.com/login?token=abc123"))
        assertEquals("", extractWebLoginToken("jingrongxianpei://other?token=abc123"))
        assertEquals("", extractWebLoginToken("not a qr code"))
    }

    @Test
    fun profile_exposes_web_login_scan_entry() {
        val screens = File("src/main/java/com/example/ui/Screens.kt").readText()
        val app = File("src/main/java/com/example/ui/SupplyApp.kt").readText()

        assertTrue(screens.contains("扫码登录网页版"))
        assertTrue(screens.contains("网页版登录设备"))
        assertTrue(screens.contains("Screen.WebQrScanner"))
        assertTrue(screens.contains("Screen.WebSessions"))
        assertTrue(app.contains("WebQrScannerScreen"))
        assertTrue(app.contains("WebSessionsScreen"))
    }
}
