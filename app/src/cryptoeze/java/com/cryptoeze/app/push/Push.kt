package com.cryptoeze.app.push

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.cryptoeze.app.R
import com.google.firebase.messaging.FirebaseMessaging

/** Firebase push for this brand. */
object Push {
    fun init(app: Application) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                app.getString(R.string.channel_general_id),
                app.getString(R.string.channel_general_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = app.getString(R.string.channel_general_desc)
                enableLights(true)
                lightColor = app.getColor(R.color.brand_teal)
            }
            app.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        FirebaseMessaging.getInstance().apply {
            token.addOnSuccessListener { PushStore.saveToken(app, it) }
            // Lets the backend / Firebase console broadcast to every user with topic "all".
            subscribeToTopic("all")
        }
    }
}
