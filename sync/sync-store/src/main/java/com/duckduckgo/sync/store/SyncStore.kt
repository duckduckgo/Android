package com.duckduckgo.sync.store

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

interface SyncStore {
    var userId: String?
    var deviceName: String?
    var deviceId: String?
    var token: String?
    var primaryKey: String?
    var secretKey: String?
    fun isSignedInFlow(): Flow<Boolean>
    fun isSignedIn(): Boolean
    fun storeCredentials(
        userId: String,
        deviceId: String,
        deviceName: String,
        primaryKey: String,
        secretKey: String,
        token: String,
    )

    fun clearAll()
}

class SyncSharedPrefsStore
constructor(
    private val sharedPrefsProv: SharedPrefsProvider,
    private val appCoroutineScope: CoroutineScope,
) : SyncStore {

    private val encryptedPreferences: SharedPreferences? by lazy { encryptedPreferences() }

    private val isSignedInStateFlow = MutableStateFlow(isSignedIn())

    init {
        appCoroutineScope.launch {
            isSignedInStateFlow.emit(isSignedIn())
        }
    }

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

    override var primaryKey: String?
        get() = encryptedPreferences?.getString(KEY_PK, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_PK)
                } else {
                    putString(KEY_PK, value)
                }
            }
        }

    override var secretKey: String?
        get() = encryptedPreferences?.getString(KEY_SK, null)
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                if (value == null) {
                    remove(KEY_SK)
                } else {
                    putString(KEY_SK, value)
                }
            }
        }

    override fun isSignedInFlow(): Flow<Boolean> = isSignedInStateFlow

    override fun isSignedIn() = !primaryKey.isNullOrEmpty() && !userId.isNullOrEmpty()

    override fun storeCredentials(
        userId: String,
        deviceId: String,
        deviceName: String,
        primaryKey: String,
        secretKey: String,
        token: String,
    ) {
        this.userId = userId
        this.deviceId = deviceId
        this.deviceName = deviceName
        this.token = token
        this.primaryKey = primaryKey
        this.secretKey = secretKey

        appCoroutineScope.launch {
            isSignedInStateFlow.emit(true)
        }
    }

    override fun clearAll() {
        encryptedPreferences?.edit(commit = true) { clear() }
        appCoroutineScope.launch {
            isSignedInStateFlow.emit(false)
        }
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.sync.store"
        private const val KEY_USER_ID = "KEY_USER_ID"
        private const val KEY_DEVICE_ID = "KEY_DEVICE_ID"
        private const val KEY_DEVICE_NAME = "KEY_DEVICE_NAME"
        private const val KEY_TOKEN = "KEY_TOKEN"
        private const val KEY_PK = "KEY_PK"
        private const val KEY_SK = "KEY_SK"
    }
}
