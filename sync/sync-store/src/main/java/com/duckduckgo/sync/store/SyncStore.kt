package com.duckduckgo.sync.store

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

interface SyncStore {
    var syncingDataEnabled: Boolean
    var userId: String?
    var deviceName: String?
    var deviceId: String?
    var token: String?
    var primaryKey: String?
    var secretKey: String?
    fun isEncryptionSupported(): Boolean
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
    private val dispatcherProvider: DispatcherProvider,
) : SyncStore {

    private val encryptedPreferences: SharedPreferences? by lazy { encryptedPreferences() }

    private val isSignedInStateFlow: MutableSharedFlow<Boolean> = MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            isSignedInStateFlow.emit(isSignedIn())
        }
    }

    @Synchronized
    private fun encryptedPreferences(): SharedPreferences? {
        return sharedPrefsProv.getEncryptedSharedPrefs(FILENAME)
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

    override var syncingDataEnabled: Boolean
        get() = encryptedPreferences?.getBoolean(KEY_SYNCING_DATA_ENABLED, true) ?: true
        set(value) {
            encryptedPreferences?.edit(commit = true) {
                putBoolean(KEY_SYNCING_DATA_ENABLED, value)
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

    override fun isEncryptionSupported(): Boolean = encryptedPreferences != null

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

        appCoroutineScope.launch(dispatcherProvider.io()) {
            isSignedInStateFlow.emit(true)
        }
    }

    override fun clearAll() {
        encryptedPreferences?.edit(commit = true) { clear() }
        appCoroutineScope.launch(dispatcherProvider.io()) {
            isSignedInStateFlow.emit(false)
        }
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.sync.store.v1"
        private const val KEY_USER_ID = "KEY_USER_ID"
        private const val KEY_DEVICE_ID = "KEY_DEVICE_ID"
        private const val KEY_DEVICE_NAME = "KEY_DEVICE_NAME"
        private const val KEY_TOKEN = "KEY_TOKEN"
        private const val KEY_SYNCING_DATA_ENABLED = "KEY_SYNCING_DATA_ENABLED"
        private const val KEY_PK = "KEY_PK"
        private const val KEY_SK = "KEY_SK"
    }
}
