package com.duckduckgo.sync.store

import android.content.SharedPreferences
import androidx.core.content.edit

interface SyncStore {
    var userId: String?
    var deviceName: String?
    var deviceId: String?
    var token: String?
}

class SyncSharedPrefsStore
constructor(
    private val sharedPrefsProv: SharedPrefsProvider,
) : SyncStore {

    private val encryptedPreferences: SharedPreferences? by lazy { encryptedPreferences() }

    @Synchronized
    private fun encryptedPreferences(): SharedPreferences? {
        return sharedPrefsProv.getSharedPrefs(FILENAME)
    }

    override var userId: String?
        get() = encryptedPreferences?.getString(KEY_USER_ID, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_USER_ID)
                } else {
                    putString(KEY_USER_ID, value)
                }
            }
        }

    override var deviceName: String?
        get() = encryptedPreferences?.getString(KEY_DEVICE_NAME, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_DEVICE_NAME)
                } else {
                    putString(KEY_DEVICE_NAME, value)
                }
            }
        }

    override var deviceId: String?
        get() = encryptedPreferences?.getString(KEY_DEVICE_ID, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_DEVICE_ID)
                } else {
                    putString(KEY_DEVICE_ID, value)
                }
            }
        }

    override var token: String?
        get() = encryptedPreferences?.getString(KEY_TOKEN, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_TOKEN)
                } else {
                    putString(KEY_TOKEN, value)
                }
            }
        }

    companion object {
        private const val FILENAME = "com.duckduckgo.sync.store"
        private const val KEY_USER_ID = "KEY_USER_ID"
        private const val KEY_DEVICE_ID = "KEY_DEVICE_ID"
        private const val KEY_DEVICE_NAME = "KEY_DEVICE_NAME"
        private const val KEY_TOKEN = "KEY_TOKEN"
    }
}
