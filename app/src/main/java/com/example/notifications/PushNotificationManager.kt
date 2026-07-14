package com.smartprocurement.internal.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import cn.jpush.android.api.JPushInterface
import com.smartprocurement.internal.BuildConfig
import com.smartprocurement.internal.MainActivity
import com.smartprocurement.internal.R

enum class PushNotificationState {
    NOT_CONSENTED,
    PERMISSION_REQUIRED,
    DISABLED,
    REGISTERING,
    CONNECTION_FAILED,
    ENABLED,
    NOT_CONFIGURED
}

class PushNotificationManager(private val context: Context) {
    fun initialize() {
        if (BuildConfig.JPUSH_APP_KEY.isBlank()) return
        createChannels()
        JPushInterface.setDebugMode(BuildConfig.DEBUG)
        JPushInterface.init(context.applicationContext)
        JPushInterface.resumePush(context.applicationContext)
    }

    fun registrationId(): String =
        if (BuildConfig.JPUSH_APP_KEY.isBlank()) "" else JPushInterface.getRegistrationID(context).orEmpty()

    fun stop() {
        if (BuildConfig.JPUSH_APP_KEY.isNotBlank()) {
            JPushInterface.stopPush(context.applicationContext)
        }
    }

    fun hasRuntimePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notificationsEnabled(): Boolean =
        hasRuntimePermission() && NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun openSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun showReconciliationNotification(change: ReconciledOrderChange) {
        if (!notificationsEnabled()) return
        createChannels()
        val event = PushEvent(change.eventId, change.eventType, change.orderId, change.status)
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("event_id", event.eventId)
            .putExtra("event_type", event.eventType)
            .putExtra("order_id", event.orderId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            context,
            event.eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, PushNotificationPolicy.channelId(event))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (event.eventType == "ORDER_CREATED") "新采购订单" else "订单状态更新")
            .setContentText(PushNotificationPolicy.message(event))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(
                if (event.eventType == "ORDER_CREATED") NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        runCatching {
            NotificationManagerCompat.from(context).notify(event.eventId.hashCode(), notification)
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel("new_orders", "新订单", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "管理员收到新的采购订单时提醒"
                },
                NotificationChannel("order_updates", "订单状态", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "子单位订单状态变化提醒"
                }
            )
        )
    }
}
