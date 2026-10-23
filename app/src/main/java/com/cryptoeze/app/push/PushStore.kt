package com.cryptoeze.app.push

import android.content.Context

/** Push token storage shared by all brands (empty when a brand has no push). */
object PushStore {
    private const val PREFS = "push"
    private const val KEY_TOKEN = "fcm_token"
    const val EXTRA_URL = "url"

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TOKEN, token).apply()
    }

    fun token(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, "").orEmpty()
}
