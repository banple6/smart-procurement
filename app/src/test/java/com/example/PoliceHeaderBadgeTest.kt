package com.smartprocurement.internal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PoliceHeaderBadgeTest {
    @Test
    fun blue_brand_header_places_the_police_badge_at_the_right_edge() {
        val source = File("src/main/java/com/example/ui/designsystem/PoliceComponents.kt").readText()

        assertTrue(source.contains("fun PoliceBrandHeader("))
        assertTrue(source.contains("fun PoliceIdentityHeader("))
        assertTrue(source.contains("fun PoliceHeaderBrandMark("))
        assertTrue(source.contains("R.drawable.police_badge_small"))
        assertTrue(source.contains("contentDescription = \"公安徽章\""))
        assertTrue(source.contains("Modifier.size(40.dp)"))
        assertTrue(source.contains("text = \"三公鲜配\""))
    }
}
