package com.smartprocurement.internal.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.smartprocurement.internal.BuildConfig
import com.smartprocurement.internal.data.ProcurementApiClient
import com.smartprocurement.internal.data.PushPreferences
import com.smartprocurement.internal.data.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PushRegistrationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val session = SessionStore(applicationContext).sessionFlow.first() ?: return Result.success()
        if (session.token.isBlank() || session.mustChangePassword) return Result.success()
        if (session.expiresAt > 0 && session.expiresAt <= System.currentTimeMillis() / 1000) return Result.success()

        val preferences = PushPreferences(applicationContext)
        val state = preferences.state.first()
        if (!state.privacyConsented) return Result.success()

        val manager = PushNotificationManager(applicationContext)
        manager.initialize()
        val registrationId = state.registrationId.ifBlank { manager.registrationId() }
        if (registrationId.isBlank()) return Result.retry()
        if (registrationId != state.registrationId) preferences.saveRegistrationId(registrationId)

        val installationId = preferences.getOrCreateInstallationId()
        val registered = runCatching {
            withContext(Dispatchers.IO) {
                ProcurementApiClient().registerPushDevice(
                    token = session.token,
                    registrationId = registrationId,
                    installationId = installationId,
                    appVersion = BuildConfig.VERSION_NAME
                )
            }
        }.isSuccess
        if (!registered) return Result.retry()

        val currentSession = SessionStore(applicationContext).sessionFlow.first()
        if (currentSession?.userId != session.userId || currentSession.token != session.token) return Result.success()
        preferences.markRegistered()
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK = "push-device-registration"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<PushRegistrationWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }
    }
}
