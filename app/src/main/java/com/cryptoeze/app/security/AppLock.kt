package com.cryptoeze.app.security

import android.content.Context
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.cryptoeze.app.R

/**
 * Fingerprint / face / screen-lock protection. Only active once the user is signed in
 * and only if the phone has a screen lock set up, so it never gets in the way of login.
 */
class AppLock(private val activity: FragmentActivity) {

    private val prefs = activity.getSharedPreferences("app_lock", Context.MODE_PRIVATE)
    private var backgroundedAt = 0L
    private var prompting = false
    var locked = false
        private set

    var signedIn: Boolean
        get() = prefs.getBoolean(KEY_SIGNED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_SIGNED_IN, value).apply()

    private val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    private fun available(): Boolean =
        BiometricManager.from(activity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    /** Should the app be locked right now (cold start or back after [TIMEOUT_MS] in background)? */
    fun shouldLock(coldStart: Boolean): Boolean {
        if (!signedIn || !available()) return false
        if (coldStart) return true
        return backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > TIMEOUT_MS
    }

    fun onBackground() {
        if (!locked) backgroundedAt = SystemClock.elapsedRealtime()
    }

    fun markLocked() {
        locked = true
    }

    fun prompt(onUnlocked: () -> Unit) {
        if (prompting) return
        prompting = true
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    locked = false
                    backgroundedAt = 0L
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.unlock_prompt_title))
            .setSubtitle(activity.getString(R.string.unlock_prompt_subtitle))
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(info)
    }

    companion object {
        private const val KEY_SIGNED_IN = "signed_in"
        private const val TIMEOUT_MS = 60_000L

    }
}
