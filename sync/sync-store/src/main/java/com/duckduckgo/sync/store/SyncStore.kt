package com.duckduckgo.sync.store

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface SyncStore {
    suspend fun getSyncingDataEnabled(): Boolean
    suspend fun setSyncingDataEnabled(enabled: Boolean)
    suspend fun getUserId(): String?
    suspend fun setUserId(userId: String?)
    suspend fun getDeviceName(): String?
    suspend fun setDeviceName(deviceName: String?)
    suspend fun getDeviceId(): String?
    suspend fun setDeviceId(deviceId: String?)
    suspend fun getToken(): String?
    suspend fun setToken(token: String?)
    suspend fun getPrimaryKey(): String?
    suspend fun setPrimaryKey(primaryKey: String?)
    suspend fun getSecretKey(): String?
    suspend fun setSecretKey(secretKey: String?)
    suspend fun isEncryptionSupported(): Boolean
    fun isSignedInFlow(): Flow<Boolean>
    suspend fun isSignedIn(): Boolean
    suspend fun storeCredentials(
        userId: String,
        deviceId: String,
        deviceName: String,
        primaryKey: String,
        secretKey: String,
        token: String,
    )

    suspend fun clearAll()
}

class SyncSharedPrefsStore(
    private val sharedPrefsProv: SharedPrefsProvider,
    private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val createAsyncPreferences: Boolean,
) : SyncStore {

    private val mutex: Mutex = Mutex()
    private val encryptedPreferencesDeferred: Deferred<SharedPreferences?> by lazy {
        appCoroutineScope.async(dispatcherProvider.io()) {
            encryptedPreferencesAsync()
        }
    }

    private val encryptedPreferencesSync: SharedPreferences? by lazy { encryptedPreferencesSync() }

    private suspend fun getEncryptedPreferences(): SharedPreferences? {
        return withContext(dispatcherProvider.io()) {
            if (createAsyncPreferences) encryptedPreferencesDeferred.await() else encryptedPreferencesSync
        }
    }

    private suspend fun encryptedPreferencesAsync(): SharedPreferences? {
        return mutex.withLock {
            sharedPrefsProv.getEncryptedSharedPrefs(FILENAME)
        }
    }

    @Synchronized
    private fun encryptedPreferencesSync(): SharedPreferences? {
        return sharedPrefsProv.getEncryptedSharedPrefs(FILENAME)
    }

    private val isSignedInStateFlow: MutableSharedFlow<Boolean> = MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            isSignedInStateFlow.emit(isSignedIn())
        }
    }

    override suspend fun getUserId(): String? {
        return getEncryptedPreferences()?.getString(KEY_USER_ID, null)
    }

    override suspend fun setUserId(userId: String?) {
        getEncryptedPreferences()?.edit(commit = true) {
            if (userId == null) {
                remove(KEY_USER_ID)
            } else {
                putString(KEY_USER_ID, userId)
            }
        }
    }

    override suspend fun getDeviceName(): String? {
        return getEncryptedPreferences()?.getString(KEY_DEVICE_NAME, null)
    }

    override suspend fun setDeviceName(deviceName: String?) {
        getEncryptedPreferences()?.edit(commit = true) {
            if (deviceName == null) {
                remove(KEY_DEVICE_NAME)
            } else {
                putString(KEY_DEVICE_NAME, deviceName)
            }
        }
    }

    override suspend fun getDeviceId(): String? {
        return getEncryptedPreferences()?.getString(KEY_DEVICE_ID, null)
    }

    override suspend fun setDeviceId(deviceId: String?) {
        getEncryptedPreferences()?.edit(commit = true) {
            if (deviceId == null) {
                remove(KEY_DEVICE_ID)
            } else {
                putString(KEY_DEVICE_ID, deviceId)
            }
        }
    }

    override suspend fun getToken(): String? {
        return getEncryptedPreferences()?.getString(KEY_TOKEN, null)
    }

    override suspend fun setToken(token: String?) {
        getEncryptedPreferences()?.edit(commit = true) {
            if (token == null) {
                remove(KEY_TOKEN)
            } else {
                putString(KEY_TOKEN, token)
            }
        }
    }

    override suspend fun getPrimaryKey(): String? {
        return getEncryptedPreferences()?.getString(KEY_PK, null)
    }

    override suspend fun setPrimaryKey(primaryKey: String?) {
        getEncryptedPreferences()?.edit(commit = true) {
            if (primaryKey == null) {
                remove(KEY_PK)
            } else {
                putString(KEY_PK, primaryKey)
            }
        }
    }

    override suspend fun getSyncingDataEnabled(): Boolean {
        return getEncryptedPreferences()?.getBoolean(KEY_SYNCING_DATA_ENABLED, true) ?: true
    }

    override suspend fun setSyncingDataEnabled(enabled: Boolean) {
        getEncryptedPreferences()?.edit(commit = true) {
            putBoolean(KEY_SYNCING_DATA_ENABLED, enabled)
        }
    }

    override suspend fun getSecretKey(): String? {
        return getEncryptedPreferences()?.getString(KEY_SK, null)
    }

    override suspend fun setSecretKey(secretKey: String?) {
        getEncryptedPreferences()?.edit(commit = true) {
            if (secretKey == null) {
                remove(KEY_SK)
            } else {
                putString(KEY_SK, secretKey)
            }
        }
    }

    override suspend fun isEncryptionSupported(): Boolean = getEncryptedPreferences() != null

    override fun isSignedInFlow(): Flow<Boolean> = isSignedInStateFlow

    override suspend fun isSignedIn(): Boolean = !getPrimaryKey().isNullOrEmpty() && !getUserId().isNullOrEmpty()

    override suspend fun storeCredentials(
        userId: String,
        deviceId: String,
        deviceName: String,
        primaryKey: String,
        secretKey: String,
        token: String,
    ) {
        setUserId(userId)
        setDeviceId(deviceId)
        setDeviceName(deviceName)
        setToken(token)
        setPrimaryKey(primaryKey)
        setSecretKey(secretKey)

        withContext(dispatcherProvider.io()) {
            isSignedInStateFlow.emit(true)
        }
    }

    override suspend fun clearAll() {
        getEncryptedPreferences()?.edit(commit = true) { clear() }
        withContext(dispatcherProvider.io()) {
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
