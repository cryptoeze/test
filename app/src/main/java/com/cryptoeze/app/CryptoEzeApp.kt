package com.cryptoeze.app

import android.app.Application
import com.cryptoeze.app.push.Push

class CryptoEzeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Push.init(this)
    }
}
