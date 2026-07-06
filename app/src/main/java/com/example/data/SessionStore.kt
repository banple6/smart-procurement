package com.smartprocurement.internal.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.sessionDataStore by preferencesDataStore(name = "login_session")
private const val SESSION_KEY_ALIAS = "smart_procurement_session_key"

data class LoginSession(
    val userId: String,
    val rememberLogin: Boolean,
    val token: String = "",
    val expiresAt: Long = 0,
    val username: String = "",
    val displayName: String = "",
    val role: String = "",
    val unitId: String = "",
    val unitCode: String = "",
    val unitName: String = "",
    val defaultDeliveryPoint: String = "",
    val mustChangePassword: Boolean = false
)

data class OnboardingState(
    val completed: Boolean = false,
    val selectedPath: String = ""
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
    private val unitCodeKey = stringPreferencesKey("unit_code")
    private val unitNameKey = stringPreferencesKey("unit_name")
    private val defaultDeliveryPointKey = stringPreferencesKey("default_delivery_point")
    private val mustChangePasswordKey = booleanPreferencesKey("must_change_password")
    private val tokenIvKey = stringPreferencesKey("token_iv")
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
    private val selectedOnboardingPathKey = stringPreferencesKey("preferred_entry_method")

    val sessionFlow: Flow<LoginSession?> = context.sessionDataStore.data.map { preferences ->
        val userId = preferences[userIdKey].orEmpty()
        if (userId.isBlank()) null else LoginSession(
            userId = userId,
            rememberLogin = preferences[rememberKey] ?: true,
            token = decryptToken(preferences[tokenKey].orEmpty(), preferences[tokenIvKey].orEmpty()).orEmpty(),
            expiresAt = preferences[expiresAtKey]?.toLongOrNull() ?: 0,
            username = preferences[usernameKey].orEmpty(),
            displayName = preferences[displayNameKey].orEmpty(),
            role = preferences[roleKey].orEmpty(),
            unitId = preferences[unitIdKey].orEmpty(),
            unitCode = preferences[unitCodeKey].orEmpty(),
            unitName = preferences[unitNameKey].orEmpty(),
            defaultDeliveryPoint = preferences[defaultDeliveryPointKey].orEmpty(),
            mustChangePassword = preferences[mustChangePasswordKey] ?: false
        )
    }

    val onboardingFlow: Flow<OnboardingState> = context.sessionDataStore.data.map { preferences ->
        OnboardingState(
            completed = preferences[onboardingCompletedKey] ?: false,
            selectedPath = preferences[selectedOnboardingPathKey].orEmpty()
        )
    }

    suspend fun saveSession(userId: String, rememberLogin: Boolean) {
        context.sessionDataStore.edit { preferences ->
            preferences[userIdKey] = userId
            preferences[rememberKey] = rememberLogin
        }
    }

    suspend fun saveSession(session: LoginSession) {
        val encryptedToken = encryptToken(session.token)
        context.sessionDataStore.edit { preferences ->
            preferences[userIdKey] = session.userId
            preferences[rememberKey] = session.rememberLogin
            preferences[tokenKey] = encryptedToken.cipherText
            preferences[tokenIvKey] = encryptedToken.iv
            preferences[expiresAtKey] = session.expiresAt.toString()
            preferences[usernameKey] = session.username
            preferences[displayNameKey] = session.displayName
            preferences[roleKey] = session.role
            preferences[unitIdKey] = session.unitId
            preferences[unitCodeKey] = session.unitCode
            preferences[unitNameKey] = session.unitName
            preferences[defaultDeliveryPointKey] = session.defaultDeliveryPoint
            preferences[mustChangePasswordKey] = session.mustChangePassword
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { preferences ->
            preferences.remove(userIdKey)
            preferences.remove(rememberKey)
            preferences.remove(tokenKey)
            preferences.remove(tokenIvKey)
            preferences.remove(expiresAtKey)
            preferences.remove(usernameKey)
            preferences.remove(displayNameKey)
            preferences.remove(roleKey)
            preferences.remove(unitIdKey)
            preferences.remove(unitCodeKey)
            preferences.remove(unitNameKey)
            preferences.remove(defaultDeliveryPointKey)
            preferences.remove(mustChangePasswordKey)
        }
    }

    suspend fun completeOnboarding(selectedPath: String) {
        context.sessionDataStore.edit { preferences ->
            preferences[onboardingCompletedKey] = true
            preferences[selectedOnboardingPathKey] = selectedPath
        }
    }

    suspend fun resetOnboardingGuide() {
        context.sessionDataStore.edit { preferences ->
            preferences[onboardingCompletedKey] = false
            preferences.remove(selectedOnboardingPathKey)
        }
    }

    private data class EncryptedToken(val cipherText: String, val iv: String)

    private fun encryptToken(token: String): EncryptedToken {
        if (token.isBlank()) return EncryptedToken("", "")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey())
        return EncryptedToken(
            cipherText = Base64.encodeToString(cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP),
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        )
    }

    private fun decryptToken(cipherText: String, iv: String): String? {
        if (cipherText.isBlank() || iv.isBlank()) return ""
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                sessionKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun sessionKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(SESSION_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                SESSION_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
