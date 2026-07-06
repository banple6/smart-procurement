package com.smartprocurement.internal

import com.smartprocurement.internal.ui.extractWebLoginToken
import org.junit.Assert.assertEquals
import org.junit.Test

class WebLoginQrPolicyTest {
    @Test
    fun extracts_only_jingrong_web_login_tokens() {
        assertEquals("abc123", extractWebLoginToken("jingrongxianpei://web-login?token=abc123"))
        assertEquals("abc 123", extractWebLoginToken("jingrongxianpei://web-login?token=abc%20123"))
        assertEquals("", extractWebLoginToken("https://example.com/login?token=abc123"))
        assertEquals("", extractWebLoginToken("jingrongxianpei://other?token=abc123"))
        assertEquals("", extractWebLoginToken("not a qr code"))
    }
}
