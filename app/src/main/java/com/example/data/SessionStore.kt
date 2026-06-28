package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "login_session")

data class LoginSession(
    val userId: String,
    val rememberLogin: Boolean,
    val token: String = "",
    val expiresAt: Long = 0,
    val username: String = "",
    val displayName: String = "",
    val role: String = "",
    val unitId: String = ""
)

class SessionStore(private val context: Context) {
    private val userIdKey = stringPreferencesKey("user_id")
    private val rememberKey = booleanPreferencesKey("remember_login")
    private val tokenKey = stringPreferencesKey("token")
    private val expiresAtKey = stringPreferencesKey("expires_at")
    private val usernameKey = stringPreferencesKey("username")
    private val displayNameKey = stringPreferencesKey("display_name")
    private val roleKey = stringPreferencesKey("role")
    private val unitIdKey = stringPreferencesKey("unit_id")

    val sessionFlow: Flow<LoginSession?> = context.sessionDataStore.data.map { preferences ->
        val userId = preferences[userIdKey].orEmpty()
        if (userId.isBlank()) null else LoginSession(
            userId = userId,
            rememberLogin = preferences[rememberKey] ?: true,
            token = preferences[tokenKey].orEmpty(),
            expiresAt = preferences[expiresAtKey]?.toLongOrNull() ?: 0,
            username = preferences[usernameKey].orEmpty(),
            displayName = preferences[displayNameKey].orEmpty(),
            role = preferences[roleKey].orEmpty(),
            unitId = preferences[unitIdKey].orEmpty()
        )
    }

    suspend fun saveSession(userId: String, rememberLogin: Boolean) {
        context.sessionDataStore.edit { preferences ->
            preferences[userIdKey] = userId
            preferences[rememberKey] = rememberLogin
        }
    }

    suspend fun saveSession(session: LoginSession) {
        context.sessionDataStore.edit { preferences ->
            preferences[userIdKey] = session.userId
            preferences[rememberKey] = session.rememberLogin
            preferences[tokenKey] = session.token
            preferences[expiresAtKey] = session.expiresAt.toString()
            preferences[usernameKey] = session.username
            preferences[displayNameKey] = session.displayName
            preferences[roleKey] = session.role
            preferences[unitIdKey] = session.unitId
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { preferences ->
            preferences.remove(userIdKey)
            preferences.remove(rememberKey)
            preferences.remove(tokenKey)
            preferences.remove(expiresAtKey)
            preferences.remove(usernameKey)
            preferences.remove(displayNameKey)
            preferences.remove(roleKey)
            preferences.remove(unitIdKey)
        }
    }
}
