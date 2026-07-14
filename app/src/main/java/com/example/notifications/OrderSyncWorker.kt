package com.smartprocurement.internal.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.smartprocurement.internal.data.AppDatabase
import com.smartprocurement.internal.data.ProcurementApiClient
import com.smartprocurement.internal.data.PushPreferences
import com.smartprocurement.internal.data.SessionStore
import com.smartprocurement.internal.data.SupplyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class OrderSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val session = SessionStore(applicationContext).sessionFlow.first() ?: return Result.success()
        if (session.token.isBlank() || session.mustChangePassword) return Result.success()
        if (session.expiresAt > 0 && session.expiresAt <= System.currentTimeMillis() / 1000) return Result.success()

        val repository = SupplyRepository(AppDatabase.getDatabase(applicationContext))
        val before = repository.allOrders.first().map { OrderSnapshot(it.orderId, it.version, it.status) }
        val remoteOrders = runCatching {
            withContext(Dispatchers.IO) {
                ProcurementApiClient().orders(session.token, session.role == "admin")
            }
        }.getOrElse { return Result.retry() }

        val currentSession = SessionStore(applicationContext).sessionFlow.first()
        if (currentSession?.userId != session.userId || currentSession.token != session.token) {
            return Result.success()
        }

        repository.replaceOrders(remoteOrders)
        val after = remoteOrders.map { OrderSnapshot(it.order.orderId, it.order.version, it.order.status) }
        val preferences = PushPreferences(applicationContext)
        val preferenceState = preferences.state.first()
        val notificationManager = PushNotificationManager(applicationContext)
        if (!preferenceState.privacyConsented || !notificationManager.notificationsEnabled()) return Result.success()

        OrderChangeDetector.detect(session.role, before, after).forEach { change ->
            if (change.eventId !in preferences.state.first().processedEventIds) {
                notificationManager.showReconciliationNotification(change)
                preferences.addProcessedEvent(change.eventId)
            }
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK = "order-notification-reconciliation"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            // WorkManager's 15-minute interval is inexact and may be delayed by system power policies.
            val request = PeriodicWorkRequestBuilder<OrderSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        }
    }
}
