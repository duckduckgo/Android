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

import androidx.annotation.WorkerThread
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.SyncStateCallbacks
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.ACCOUNT_CREATION
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.ACCOUNT_LOGIN
import com.duckduckgo.sync.crypto.AccountKeys
import com.duckduckgo.sync.crypto.LoginKeys
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.API_CODE.INVALID_LOGIN_CREDENTIALS
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import javax.inject.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

interface SyncRepository {

    fun createAccount(): Result<Boolean>
    fun isSignedIn(): Boolean
    fun login(recoveryCodeRawJson: String): Result<Boolean>
    fun getAccountInfo(): AccountInfo
    fun removeAccount()
    fun logout(deviceId: String): Result<Boolean>
    fun deleteAccount(): Result<Boolean>
    fun latestToken(): String
    fun getRecoveryCode(): String?
    fun getThisConnectedDevice(): ConnectedDevice?
    fun getConnectedDevices(): Result<List<ConnectedDevice>>
    fun getConnectQR(): Result<String>
    fun connectDevice(contents: String): Result<Boolean>
    fun pollConnectionKeys(): Result<Boolean>
    fun renameDevice(device: ConnectedDevice): Result<Boolean>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
@WorkerThread
class AppSyncRepository @Inject constructor(
    private val syncDeviceIds: SyncDeviceIds,
    private val nativeLib: SyncLib,
    private val syncApi: SyncApi,
    private val syncStore: SyncStore,
    private val syncEngine: SyncEngine,
    private val syncStateCallbackPluginPoint: PluginPoint<SyncStateCallbacks>
) : SyncRepository {

    init {
        syncStore.isSignedInFlow().onEach { signedIn ->
            if (signedIn){
                syncStateCallbackPluginPoint.getPlugins().forEach {
                    it.onSyncEnabled()
                }
            } else {
                syncStateCallbackPluginPoint.getPlugins().forEach {
                    it.onSyncDisabled()
                }
            }
        }

    }
    override fun createAccount(): Result<Boolean> {
        val userId = syncDeviceIds.userId()

        val account: AccountKeys = nativeLib.generateAccountKeys(userId = userId)
        if (account.result != 0L) return Result.Error(code = account.result.toInt(), reason = "Account keys failed")

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
            is Result.Error -> {
                result.removeKeysIfInvalid()
                result
            }

            is Result.Success -> {
                syncStore.storeCredentials(account.userId, deviceId, deviceName, account.primaryKey, account.secretKey, result.data.token)
                syncEngine.triggerSync(ACCOUNT_CREATION)
                Result.Success(true)
            }
        }
    }

    override fun login(recoveryCodeRawJson: String): Result<Boolean> {
        val recoveryCode = kotlin.runCatching {
            Adapters.recoveryCodeAdapter.fromJson(recoveryCodeRawJson.decodeB64())?.recovery
        }.getOrNull() ?: return Result.Error(reason = "Failed to decode recovery code")

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
        ) ?: return Error(reason = "Error generating Linking Code")

        return Result.Success(linkingQRCode.encodeB64())
    }

    override fun connectDevice(contents: String): Result<Boolean> {
        val connectKeys = kotlin.runCatching {
            Adapters.recoveryCodeAdapter.fromJson(contents.decodeB64())?.connect
        }.getOrNull() ?: return Result.Error(reason = "Failed to decode connect code")

        if (!isSignedIn()) {
            val result = createAccount()
            if (result is Error) return result
        }

        val primaryKey = syncStore.primaryKey ?: return Result.Error(reason = "Error reading PK")
        val userId = syncStore.userId ?: return Result.Error(reason = "Error reading UserId")
        val token = syncStore.token ?: return Result.Error(reason = "Error token")
        val recoverKey = Adapters.recoveryCodeAdapter.toJson(LinkCode(RecoveryCode(primaryKey = primaryKey, userId = userId)))
        val seal = nativeLib.seal(recoverKey, connectKeys.secretKey)

        return syncApi.connect(token = token, deviceId = connectKeys.deviceId, publicKey = seal)
    }

    override fun pollConnectionKeys(): Result<Boolean> {
        val deviceId = syncDeviceIds.deviceId()
        val result = syncApi.connectDevice(deviceId)

        return when (result) {
            is Result.Error -> {
                result
            }

            is Result.Success -> {
                val sealOpen = nativeLib.sealOpen(result.data, syncStore.primaryKey!!, syncStore.secretKey!!)
                val recoveryCode = Adapters.recoveryCodeAdapter.fromJson(sealOpen)?.recovery ?: return Result.Error(reason = "Error reading json")
                syncStore.userId = recoveryCode.userId
                syncStore.primaryKey = recoveryCode.primaryKey
                return performLogin(recoveryCode.userId, deviceId, syncDeviceIds.deviceName(), recoveryCode.primaryKey)
            }
        }
    }

    override fun removeAccount() {
        onSyncDisabled()
    }

    override fun logout(deviceId: String): Result<Boolean> {
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: return Result.Error(reason = "Token Empty")

        val logoutThisDevice = deviceId.isEmpty() || deviceId == syncStore.deviceId

        val deviceId = if (logoutThisDevice) {
            syncStore.deviceId.takeUnless { it.isNullOrEmpty() }
                ?: return Result.Error(reason = "Device Id Empty")
        } else {
            deviceId
        }

        return when (val result = syncApi.logout(token, deviceId)) {
            is Result.Error -> {
                result.removeKeysIfInvalid()
                result
            }

            is Result.Success -> {
                if (logoutThisDevice) {
                    onSyncDisabled()
                }
                Result.Success(true)
            }
        }
    }

    override fun deleteAccount(): Result<Boolean> {
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() } ?: return Result.Error(reason = "Token Empty")

        return when (val result = syncApi.deleteAccount(token)) {
            is Result.Error -> {
                result.removeKeysIfInvalid()
                result
            }

            is Result.Success -> {
                onSyncDisabled()
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
            ?: return Result.Error(reason = "Token Empty")
        val primaryKey = syncStore.primaryKey.takeUnless { it.isNullOrEmpty() }
            ?: return Result.Error(reason = "PrimaryKey not found")

        return when (val result = syncApi.getDevices(token)) {
            is Result.Error -> {
                result.removeKeysIfInvalid()
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
        if (preLogin.result != 0L) return Result.Error(code = preLogin.result.toInt(), reason = "Login account keys failed")

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
            is Result.Error -> {
                result.removeKeysIfInvalid()
                result
            }

            is Result.Success -> {
                val decryptResult = nativeLib.decrypt(result.data.protected_encryption_key, preLogin.stretchedPrimaryKey)
                if (decryptResult.result != 0L) return Error(code = decryptResult.result.toInt(), reason = "Decrypt failed")
                syncStore.storeCredentials(userId, deviceId, deviceName, preLogin.primaryKey, decryptResult.decryptedData, result.data.token)
                syncEngine.triggerSync(ACCOUNT_LOGIN)

                Result.Success(true)
            }
        }
    }

    private fun Error.removeKeysIfInvalid() {
        if (code == INVALID_LOGIN_CREDENTIALS.code) {
            onSyncDisabled()
        }
    }

    private fun onSyncDisabled() {
        syncStore.clearAll()
        syncEngine.onSyncDisabled()
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
