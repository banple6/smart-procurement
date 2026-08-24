package com.smartprocurement.internal

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.smartprocurement.internal.ui.SupplyViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainUiStateRestorationTest {

    @Test
    fun mainTabAndAnalyticsFiltersSurviveViewModelRecreation() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val savedState = SavedStateHandle()
        val first = SupplyViewModel(application, savedState)

        first.currentTab = "analytics"
        first.selectAnalyticsTab("price")
        first.setAnalyticsRange(90)
        first.setAnalyticsFilters(unitId = "unit-1", category = "蔬菜")
        first.updateAnalyticsUnitSort("products")
        first.saveAnalyticsScroll(index = 6, offset = 42)

        val restoredState = SavedStateHandle(
            savedState.keys().associateWith { key -> savedState.get<Any?>(key) }
        )
        val restored = SupplyViewModel(application, restoredState)

        assertEquals("analytics", restored.currentTab)
        assertEquals("price", restored.analyticsSelectedTab)
        assertEquals(90, restored.analyticsRange.days)
        assertEquals("unit-1", restored.analyticsUnitId)
        assertEquals("蔬菜", restored.analyticsCategory)
        assertEquals("products", restored.analyticsUnitSort)
        assertEquals(6, restored.analyticsScrollIndex)
        assertEquals(42, restored.analyticsScrollOffset)
    }
}
