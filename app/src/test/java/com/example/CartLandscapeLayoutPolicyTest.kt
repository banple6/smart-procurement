package com.smartprocurement.internal

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CartLandscapeLayoutPolicyTest {
    @Test
    fun cart_uses_a_compact_header_and_prioritizes_item_rows_in_landscape() {
        val cartSource = File("src/main/java/com/example/ui/CartAndOrder.kt").readText()
        val homeSource = File("src/main/java/com/example/ui/HomeAndDetail.kt").readText()
        val headerSource = File("src/main/java/com/example/ui/designsystem/PoliceComponents.kt").readText()

        assertTrue(cartSource.contains("val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp"))
        assertTrue(cartSource.contains("compact = isLandscape"))
        assertTrue(cartSource.contains("PrimaryActionDock(compact = isLandscape)"))
        assertTrue(cartSource.contains("if (isLandscape) {\n                                        QuantityStepper("))
        assertTrue(cartSource.contains("if (!isLandscape) {\n                                    Row("))
        assertTrue(cartSource.indexOf("items(rows, key") < cartSource.indexOf("DocumentSection(title = \"结算摘要\")"))
        assertTrue(headerSource.contains("compact: Boolean = false"))
        assertTrue(headerSource.contains("then(if (compact) Modifier else Modifier.statusBarsPadding())"))
        assertTrue(homeSource.contains("PoliceBrandHeader(title = title, subtitle = subtitle, compact = isLandscape)"))
        assertTrue(homeSource.contains("if (!isLandscape) {\n                item {\n                    ThinkingOrbPreviewShortcut"))
    }
}
