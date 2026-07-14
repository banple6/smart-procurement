package com.smartprocurement.internal.notifications

import android.content.Context
import android.content.Intent
import cn.jpush.android.api.NotificationMessage
import cn.jpush.android.service.JPushMessageReceiver
import com.smartprocurement.internal.MainActivity
import com.smartprocurement.internal.data.PushPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class OrderPushReceiver : JPushMessageReceiver() {
    override fun onRegister(context: Context, registrationId: String) {
        if (registrationId.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            PushPreferences(context.applicationContext).saveRegistrationId(registrationId)
            PushRegistrationWorker.schedule(context.applicationContext)
        }
    }

    override fun onNotifyMessageOpened(context: Context, message: NotificationMessage) {
        val event = parseEvent(message.notificationExtras) ?: return
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .putExtra("event_id", event.eventId)
                .putExtra("event_type", event.eventType)
                .putExtra("order_id", event.orderId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
    }

    private fun parseEvent(rawExtras: String?): PushEvent? {
        val json = runCatching { JSONObject(rawExtras.orEmpty()) }.getOrNull() ?: return null
        return PushDeepLinkPolicy.fromExtras(
            mapOf(
                "event_id" to json.optString("event_id"),
                "event_type" to json.optString("event_type"),
                "order_id" to json.optString("order_id")
            )
        )
    }
}
