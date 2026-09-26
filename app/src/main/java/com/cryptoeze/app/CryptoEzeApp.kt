package com.cryptoeze.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.cryptoeze.app.push.PushService
import com.google.firebase.messaging.FirebaseMessaging

class CryptoEzeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        FirebaseMessaging.getInstance().apply {
            token.addOnSuccessListener { PushService.saveToken(this@CryptoEzeApp, it) }
            // Lets the backend / Firebase console broadcast to every user with topic "all".
            subscribeToTopic("all")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            getString(R.string.channel_general_id),
            getString(R.string.channel_general_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.channel_general_desc)
            enableLights(true)
            lightColor = getColor(R.color.brand_teal)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
