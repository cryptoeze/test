package com.cryptoeze.app.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cryptoeze.app.MainActivity
import com.cryptoeze.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives Firebase push notifications.
 * Send `data: { url: "https://cryptoeze.com/..." }` to open a specific page on tap.
 */
class PushService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        saveToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"] ?: return
        val url = message.data["url"] ?: message.notification?.link?.toString()
        show(this, title, body, url)
    }

    companion object {
        private const val PREFS = "push"
        private const val KEY_TOKEN = "fcm_token"
        const val EXTRA_URL = "url"

        fun saveToken(context: Context, token: String) {
            context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_TOKEN, token).apply()
        }

        fun token(context: Context): String =
            context.getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_TOKEN, "").orEmpty()

        fun show(context: Context, title: String, body: String, url: String?) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED &&
                android.os.Build.VERSION.SDK_INT >= 33
            ) return

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (url != null) putExtra(EXTRA_URL, url)
            }
            val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            val pending = PendingIntent.getActivity(
                context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, context.getString(R.string.channel_general_id))
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.brand_teal))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}
