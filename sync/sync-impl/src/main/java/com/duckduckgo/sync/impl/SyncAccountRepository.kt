/*
 * Copyright (c) 2023 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.sync.impl

import androidx.annotation.*
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.*
import com.duckduckgo.sync.api.engine.*
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.ACCOUNT_CREATION
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.ACCOUNT_LOGIN
import com.duckduckgo.sync.crypto.*
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.pixels.*
import com.duckduckgo.sync.store.*
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.*
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.*
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.*
import dagger.SingleInstanceIn
import javax.inject.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

interface SyncAccountRepository {

    fun createAccount(): Result<Boolean>
    fun isSignedIn(): Boolean
    fun processCode(stringCode: String): Result<Boolean>
    fun getAccountInfo(): AccountInfo
    fun logout(deviceId: String): Result<Boolean>
    fun deleteAccount(): Result<Boolean>
    fun latestToken(): String
    fun getRecoveryCode(): String?
    fun getThisConnectedDevice(): ConnectedDevice?
    fun getConnectedDevices(): Result<List<ConnectedDevice>>
    fun getConnectQR(): Result<String>
    fun pollConnectionKeys(): Result<Boolean>
    fun renameDevice(device: ConnectedDevice): Result<Boolean>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
@WorkerThread
class AppSyncAccountRepository @Inject constructor(
    private val syncDeviceIds: SyncDeviceIds,
    private val nativeLib: SyncLib,
    private val syncApi: SyncApi,
    private val syncStore: SyncStore,
    private val syncEngine: SyncEngine,
    private val syncPixels: SyncPixels,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : SyncAccountRepository {

    override fun createAccount(): Result<Boolean> {
        val userId = syncDeviceIds.userId()

        val account: AccountKeys = nativeLib.generateAccountKeys(userId = userId)
        if (account.result != 0L) {
            return Error(code = account.result.toInt(), reason = "Create Account: keys failed").also {
                syncPixels.fireSyncAccountErrorPixel(it)
            }
        }

        val deviceId = syncDeviceIds.deviceId()
        val deviceName = syncDeviceIds.deviceName()
        val deviceType = syncDeviceIds.deviceType()
        val encryptedDeviceName = nativeLib.encryptData(deviceName, account.primaryKey).encryptedData
        val encryptedDeviceType = nativeLib.encryptData(deviceType.deviceFactor, account.primaryKey).encryptedData

        val result = syncApi.createAccount(
            account.userId,
            account.passwordHash,
            account.protectedSecretKey,
            deviceId,
            encryptedDeviceName,
            encryptedDeviceType,
        )

        return when (result) {
            is Error -> {
                syncPixels.fireSyncAccountErrorPixel(result)
                result
            }

            is Result.Success -> {
                syncStore.storeCredentials(account.userId, deviceId, deviceName, account.primaryKey, account.secretKey, result.data.token)
                syncEngine.triggerSync(ACCOUNT_CREATION)
                Timber.d("Sync-Account: recovery code is ${getRecoveryCode()}")
                Result.Success(true)
            }
        }
    }

    override fun processCode(stringCode: String): Result<Boolean> {
        kotlin.runCatching {
            Adapters.recoveryCodeAdapter.fromJson(stringCode.decodeB64())?.recovery
        }.getOrNull()?.let {
            return login(it)
        }

        kotlin.runCatching {
            Adapters.recoveryCodeAdapter.fromJson(stringCode.decodeB64())?.connect
        }.getOrNull()?.let {
            return connectDevice(it)
        }

        return Error(reason = "Failed to decode recovery code")
    }

    private fun login(recoveryCode: RecoveryCode): Result<Boolean> {
        if (isSignedIn()) {
            return Error(reason = "Already signed in")
        }

        val primaryKey = recoveryCode.primaryKey
        val userId = recoveryCode.userId
        val deviceId = syncDeviceIds.deviceId()
        val deviceName = syncDeviceIds.deviceName()

        return performLogin(userId, deviceId, deviceName, primaryKey)
    }

    override fun renameDevice(device: ConnectedDevice): Result<Boolean> {
        val userId = syncStore.userId ?: return Error(reason = "Not existing userId")
        val primaryKey = syncStore.primaryKey ?: return Error(reason = "Not existing primaryKey")
        return performLogin(userId, device.deviceId, device.deviceName, primaryKey)
    }

    override fun getAccountInfo(): AccountInfo {
        if (!isSignedIn()) return AccountInfo()

        return AccountInfo(
            userId = syncStore.userId.orEmpty(),
            deviceName = syncStore.deviceName.orEmpty(),
            deviceId = syncStore.deviceId.orEmpty(),
            isSignedIn = isSignedIn(),
            primaryKey = syncStore.primaryKey.orEmpty(),
            secretKey = syncStore.secretKey.orEmpty(),
        )
    }

    override fun getRecoveryCode(): String? {
        val primaryKey = syncStore.primaryKey ?: return null
        val userID = syncStore.userId ?: return null
        return Adapters.recoveryCodeAdapter.toJson(LinkCode(RecoveryCode(primaryKey, userID))).encodeB64()
    }

    override fun getConnectQR(): Result<String> {
        val prepareForConnect = nativeLib.prepareForConnect()
        val deviceId = syncDeviceIds.deviceId()
        syncStore.deviceId = deviceId
        syncStore.primaryKey = prepareForConnect.publicKey
        syncStore.secretKey = prepareForConnect.secretKey

        val linkingQRCode = Adapters.recoveryCodeAdapter.toJson(
            LinkCode(connect = ConnectCode(deviceId = deviceId, secretKey = prepareForConnect.publicKey)),
        ) ?: return Error(reason = "Error generating Linking Code").also {
            syncPixels.fireSyncAccountErrorPixel(it)
        }

        return Result.Success(linkingQRCode.encodeB64())
    }

    private fun connectDevice(connectKeys: ConnectCode): Result<Boolean> {
        if (!isSignedIn()) {
            val result = createAccount()
            if (result is Error) return result
        }

        val primaryKey = syncStore.primaryKey ?: return Error(reason = "Error reading PK")
        val userId = syncStore.userId ?: return Error(reason = "Error reading UserId")
        val token = syncStore.token ?: return Error(reason = "Error token")
        val recoverKey = Adapters.recoveryCodeAdapter.toJson(LinkCode(RecoveryCode(primaryKey = primaryKey, userId = userId)))
        val seal = nativeLib.seal(recoverKey, connectKeys.secretKey)

        val result = syncApi.connect(token = token, deviceId = connectKeys.deviceId, publicKey = seal)
        if (result is Error) {
            syncPixels.fireSyncAccountErrorPixel(result)
        }

        return result
    }

    override fun pollConnectionKeys(): Result<Boolean> {
        val deviceId = syncDeviceIds.deviceId()
        val result = syncApi.connectDevice(deviceId)

        return when (result) {
            is Error -> {
                result
            }

            is Result.Success -> {
                val sealOpen = nativeLib.sealOpen(result.data, syncStore.primaryKey!!, syncStore.secretKey!!)
                val recoveryCode = Adapters.recoveryCodeAdapter.fromJson(sealOpen)?.recovery
                    ?: return Error(reason = "Error reading json")
                syncStore.userId = recoveryCode.userId
                syncStore.primaryKey = recoveryCode.primaryKey
                return performLogin(recoveryCode.userId, deviceId, syncDeviceIds.deviceName(), recoveryCode.primaryKey)
            }
        }
    }

    override fun logout(deviceId: String): Result<Boolean> {
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: return Error(reason = "Token Empty")

        val logoutThisDevice = deviceId.isEmpty() || deviceId == syncStore.deviceId

        val deviceId = if (logoutThisDevice) {
            syncStore.deviceId.takeUnless { it.isNullOrEmpty() }
                ?: return Error(reason = "Logout: Device Id Empty").also {
                    syncPixels.fireSyncAccountErrorPixel(it)
                }
        } else {
            deviceId
        }

        return when (val result = syncApi.logout(token, deviceId)) {
            is Error -> {
                syncPixels.fireSyncAccountErrorPixel(result)
                result
            }

            is Result.Success -> {
                if (logoutThisDevice) {
                    syncStore.clearAll()
                }
                Result.Success(true)
            }
        }
    }

    override fun deleteAccount(): Result<Boolean> {
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() } ?: return Error(reason = "Token Empty")

        return when (val result = syncApi.deleteAccount(token)) {
            is Error -> {
                syncPixels.fireSyncAccountErrorPixel(result)
                result
            }

            is Result.Success -> {
                syncStore.clearAll()
                Result.Success(true)
            }
        }
    }

    override fun latestToken(): String {
        return syncStore.token ?: ""
    }

    override fun getThisConnectedDevice(): ConnectedDevice? {
        if (!isSignedIn()) return null
        return ConnectedDevice(
            thisDevice = true,
            deviceName = syncStore.deviceName.orEmpty(),
            deviceType = syncDeviceIds.deviceType(),
            deviceId = syncStore.deviceId.orEmpty(),
        )
    }

    override fun getConnectedDevices(): Result<List<ConnectedDevice>> {
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: return Error(reason = "Token Empty")
        val primaryKey = syncStore.primaryKey.takeUnless { it.isNullOrEmpty() }
            ?: return Error(reason = "PrimaryKey not found")

        return when (val result = syncApi.getDevices(token)) {
            is Error -> {
                syncPixels.fireSyncAccountErrorPixel(result)
                result
            }

            is Result.Success -> {
                return Result.Success(
                    result.data.map {
                        ConnectedDevice(
                            thisDevice = syncStore.deviceId == it.deviceId,
                            deviceName = nativeLib.decryptData(it.deviceName, primaryKey).decryptedData,
                            deviceId = it.deviceId,
                            deviceType = it.deviceType.takeUnless { it.isNullOrEmpty() }?.let { encryptedDeviceType ->
                                DeviceType(nativeLib.decryptData(encryptedDeviceType, primaryKey).decryptedData)
                            } ?: DeviceType(),
                        )
                    }.sortedWith { a, b ->
                        if (a.thisDevice) -1 else 1
                    },
                )
            }
        }
    }

    override fun isSignedIn() = syncStore.isSignedIn()

    private fun performLogin(
        userId: String,
        deviceId: String,
        deviceName: String,
        primaryKey: String,
    ): Result<Boolean> {
        val preLogin: LoginKeys = nativeLib.prepareForLogin(primaryKey)
        if (preLogin.result != 0L) {
            return Error(code = preLogin.result.toInt(), reason = "Login account keys failed").also {
                syncPixels.fireSyncAccountErrorPixel(it)
            }
        }

        val deviceType = syncDeviceIds.deviceType()
        val encryptedDeviceType = nativeLib.encryptData(deviceType.deviceFactor, preLogin.primaryKey).encryptedData

        val result = syncApi.login(
            userID = userId,
            hashedPassword = preLogin.passwordHash,
            deviceId = deviceId,
            deviceName = nativeLib.encryptData(deviceName, preLogin.primaryKey).encryptedData,
            deviceType = encryptedDeviceType,
        )

        return when (result) {
            is Error -> {
                syncPixels.fireSyncAccountErrorPixel(result)
                result
            }

            is Result.Success -> {
                val decryptResult = nativeLib.decrypt(result.data.protected_encryption_key, preLogin.stretchedPrimaryKey)
                if (decryptResult.result != 0L) {
                    return Error(code = decryptResult.result.toInt(), reason = "Decrypt failed").also {
                        syncPixels.fireSyncAccountErrorPixel(it)
                    }
                }
                syncStore.storeCredentials(userId, deviceId, deviceName, preLogin.primaryKey, decryptResult.decryptedData, result.data.token)
                appCoroutineScope.launch(dispatcherProvider.io()) {
                    syncEngine.triggerSync(ACCOUNT_LOGIN)
                }

                Result.Success(true)
            }
        }
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().build()
            val recoveryCodeAdapter: JsonAdapter<LinkCode> = moshi.adapter(LinkCode::class.java)
        }
    }
}

data class AccountInfo(
    val userId: String = "",
    val deviceName: String = "",
    val deviceId: String = "",
    val isSignedIn: Boolean = false,
    val primaryKey: String = "",
    val secretKey: String = "",
)

data class LinkCode(
    val recovery: RecoveryCode? = null,
    val connect: ConnectCode? = null,
)

data class RecoveryCode(
    @field:Json(name = "primary_key") val primaryKey: String,
    @field:Json(name = "user_id") val userId: String,
)

data class ConnectedDevice(
    val thisDevice: Boolean = false,
    val deviceName: String,
    val deviceId: String,
    val deviceType: DeviceType,
)

data class ConnectCode(
    @field:Json(name = "device_id") val deviceId: String,
    @field:Json(name = "secret_key") val secretKey: String,
)

sealed class Result<out R> {

    data class Success<out T>(val data: T) : Result<T>()
    data class Error(
        val code: Int = -1,
        val reason: String,
    ) : Result<Nothing>()

    override fun toString(): String {
        return when (this) {
            is Success<*> -> "Success[data=$data]"
            is Error -> "Error[exception=$code, $reason]"
        }
    }
}
