package com.duckduckgo.sync.store

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SyncEncryptedSharedPrefsStore constructor(
        private val context: Context,
) {

    private val encryptedPreferences: SharedPreferences? by lazy { encryptedPreferences() }

    @Synchronized
    private fun encryptedPreferences(): SharedPreferences? {
        return try {
            EncryptedSharedPreferences.create(
                    context,
                    FILENAME,
                    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val FILENAME = "com.duckduckgo.sync.store"
    }
}