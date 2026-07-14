package com.smartprocurement.internal.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.smartprocurement.internal.notifications.ProcessedEventIds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.pushDataStore by preferencesDataStore(name = "push_preferences")

data class PushPreferenceState(
    val installationId: String = "",
    val privacyConsented: Boolean = false,
    val registrationId: String = "",
    val pendingRegistration: Boolean = false,
    val pendingUnbind: Boolean = false,
    val processedEventIds: List<String> = emptyList()
)

class PushPreferences(private val context: Context) {
    private val installationIdKey = stringPreferencesKey("installation_id")
    private val privacyConsentedKey = booleanPreferencesKey("privacy_consented")
    private val registrationIdKey = stringPreferencesKey("registration_id")
    private val pendingRegistrationKey = booleanPreferencesKey("pending_registration")
    private val pendingUnbindKey = booleanPreferencesKey("pending_unbind")
    private val processedEventsKey = stringPreferencesKey("processed_event_ids")

    val state: Flow<PushPreferenceState> = context.pushDataStore.data.map { values ->
        PushPreferenceState(
            installationId = values[installationIdKey].orEmpty(),
            privacyConsented = values[privacyConsentedKey] ?: false,
            registrationId = values[registrationIdKey].orEmpty(),
            pendingRegistration = values[pendingRegistrationKey] ?: false,
            pendingUnbind = values[pendingUnbindKey] ?: false,
            processedEventIds = values[processedEventsKey]
                .orEmpty()
                .lineSequence()
                .filter(String::isNotBlank)
                .toList()
                .takeLast(ProcessedEventIds.MAX_SIZE)
        )
    }

    suspend fun getOrCreateInstallationId(): String {
        var result = ""
        context.pushDataStore.edit { values ->
            result = values[installationIdKey].orEmpty().ifBlank { UUID.randomUUID().toString() }
            values[installationIdKey] = result
        }
        return result
    }

    suspend fun setPrivacyConsented(consented: Boolean) {
        context.pushDataStore.edit { it[privacyConsentedKey] = consented }
    }

    suspend fun saveRegistrationId(registrationId: String) {
        context.pushDataStore.edit { values ->
            values[registrationIdKey] = registrationId
            values[pendingRegistrationKey] = registrationId.isNotBlank()
        }
    }

    suspend fun markRegistered() {
        context.pushDataStore.edit { it[pendingRegistrationKey] = false }
    }

    suspend fun markPendingUnbind() {
        context.pushDataStore.edit { it[pendingUnbindKey] = true }
    }

    suspend fun clearPendingUnbind() {
        context.pushDataStore.edit { it[pendingUnbindKey] = false }
    }

    suspend fun addProcessedEvent(eventId: String) {
        if (eventId.isBlank()) return
        context.pushDataStore.edit { values ->
            val processed = ProcessedEventIds(
                values[processedEventsKey].orEmpty().lineSequence().filter(String::isNotBlank).toList()
            )
            processed.add(eventId)
            values[processedEventsKey] = processed.values().joinToString("\n")
        }
    }

    suspend fun clearAccountState() {
        context.pushDataStore.edit { values ->
            values.remove(processedEventsKey)
            if (values[registrationIdKey].orEmpty().isNotBlank()) {
                values[pendingRegistrationKey] = true
            }
        }
    }

    suspend fun clearForTest() {
        context.pushDataStore.edit { it.clear() }
    }
}
