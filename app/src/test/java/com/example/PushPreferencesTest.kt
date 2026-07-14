package com.smartprocurement.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.smartprocurement.internal.data.PushPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PushPreferencesTest {
    private lateinit var preferences: PushPreferences

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preferences = PushPreferences(context)
        preferences.clearForTest()
    }

    @Test
    fun installation_id_is_stable_for_the_same_installation() = runTest {
        val first = preferences.getOrCreateInstallationId()
        val second = preferences.getOrCreateInstallationId()

        assertTrue(first.isNotBlank())
        assertEquals(first, second)
    }

    @Test
    fun push_state_tracks_consent_registration_and_pending_unbind() = runTest {
        assertFalse(preferences.state.first().privacyConsented)

        preferences.setPrivacyConsented(true)
        preferences.saveRegistrationId("registration-0001")
        var state = preferences.state.first()
        assertTrue(state.privacyConsented)
        assertEquals("registration-0001", state.registrationId)
        assertTrue(state.pendingRegistration)

        preferences.markRegistered()
        preferences.markPendingUnbind()
        state = preferences.state.first()
        assertFalse(state.pendingRegistration)
        assertTrue(state.pendingUnbind)

        preferences.clearPendingUnbind()
        assertFalse(preferences.state.first().pendingUnbind)
    }

    @Test
    fun processed_events_are_unique_and_capped_without_clearing_installation_id() = runTest {
        val installationId = preferences.getOrCreateInstallationId()
        repeat(205) { preferences.addProcessedEvent("event-$it") }
        preferences.addProcessedEvent("event-204")

        val state = preferences.state.first()
        assertEquals(200, state.processedEventIds.size)
        assertFalse(state.processedEventIds.contains("event-0"))
        assertTrue(state.processedEventIds.contains("event-204"))

        preferences.clearAccountState()
        val afterClear = preferences.state.first()
        assertTrue(afterClear.processedEventIds.isEmpty())
        assertEquals(installationId, afterClear.installationId)
        assertNotEquals("", afterClear.installationId)
    }
}
