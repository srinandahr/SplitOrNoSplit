package com.srinandahr.splitornosplit.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Credential storage backed by the Android keystore.
 *
 * v1 kept the Splitwise token in plain SharedPreferences. A project private code is a
 * stronger secret than that token was — it grants delete on the whole group ledger — so
 * it lives here instead.
 *
 * If the keystore is unavailable (a real failure mode on some OEM builds, and after a
 * restore-to-new-device), we fall back to plain prefs rather than crashing on launch.
 * The app keeps working; the credential is simply no longer encrypted at rest.
 */
object SecurePrefs {

    private const val SECURE_FILE = "sons_secure_prefs"
    private const val FALLBACK_FILE = "sons_prefs_fallback"
    private const val TAG = "SecurePrefs"

    @Volatile
    private var cached: SharedPreferences? = null

    @Volatile
    var usingFallback: Boolean = false
        private set

    fun get(context: Context): SharedPreferences {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val prefs = try {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context.applicationContext,
                    SECURE_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: Exception) {
                // Keystore unusable — don't take the app down over it.
                Log.w(TAG, "Encrypted prefs unavailable, falling back to plain storage", e)
                usingFallback = true
                context.applicationContext
                    .getSharedPreferences(FALLBACK_FILE, Context.MODE_PRIVATE)
            }
            cached = prefs
            return prefs
        }
    }
}
