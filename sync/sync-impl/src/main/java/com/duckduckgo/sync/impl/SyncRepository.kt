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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.crypto.AccountKeys
import com.duckduckgo.sync.crypto.ConnectKeys
import com.duckduckgo.sync.crypto.LoginKeys
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.*
import kotlin.DeprecationLevel.WARNING
import timber.log.Timber

interface SyncRepository {
    fun createAccount(): Result<Boolean>

    @Deprecated(message = "Method only used for testing purposes. Relies on a local stored recovery key.", level = DeprecationLevel.WARNING)
    fun login(): Result<Boolean>
    fun login(recoveryCodeRawJson: String): Result<Boolean>
    fun getAccountInfo(): AccountInfo

    @Deprecated(message = "Method only used for testing purposes.", level = DeprecationLevel.WARNING)
    fun storeRecoveryCode()
    fun removeAccount()
    fun logout(deviceId: String): Result<Boolean>
    fun deleteAccount(): Result<Boolean>
    fun latestToken(): String
    fun getRecoveryCode(): String?
    fun getConnectedDevices(): Result<List<ConnectedDevice>>
    fun getLinkingQR(): String
}

@ContributesBinding(AppScope::class)
@WorkerThread
class AppSyncRepository @Inject constructor(
    private val syncDeviceIds: SyncDeviceIds,
    private val nativeLib: SyncLib,
    private val syncApi: SyncApi,
    private val syncStore: SyncStore,
) : SyncRepository {

    override fun createAccount(): Result<Boolean> {
        val userId = syncDeviceIds.userId()
        val deviceId = syncDeviceIds.deviceId()
        val deviceName = syncDeviceIds.deviceName()

        val account: AccountKeys = nativeLib.generateAccountKeys(userId = userId)
        if (account.result != 0L) return Result.Error(code = account.result.toInt(), reason = "Account keys failed")

        val result = syncApi.createAccount(
            account.userId,
            account.passwordHash,
            account.protectedSecretKey,
            deviceId,
            deviceName,
        )

        return when (result) {
            is Result.Error -> {
                Timber.i("SYNC signup failed $result")
                result
            }

            is Result.Success -> {
                syncStore.userId = userId
                syncStore.deviceId = deviceId
                syncStore.deviceName = deviceName
                syncStore.token = result.data.token
                syncStore.primaryKey = account.primaryKey
                syncStore.secretKey = account.secretKey
                Result.Success(true)
            }
        }
    }

    @Deprecated("Method only used for testing purposes. Relies on a local stored recovery key.", level = WARNING)
    override fun login(): Result<Boolean> {
        val recoveryCodeJson = syncStore.recoveryCode ?: return Result.Error(reason = "Not existing recovery code")
        val recoveryCode =
            Adapters.recoveryCodeAdapter.fromJson(recoveryCodeJson)?.recovery ?: return Result.Error(reason = "Failed reading json recovery code")

        val primaryKey = recoveryCode.primaryKey
        val userId = recoveryCode.userId
        val deviceId = syncDeviceIds.deviceId()
        val deviceName = syncDeviceIds.deviceName()

        return performLogin(userId, deviceId, deviceName, primaryKey)
    }

    override fun login(recoveryCodeRawJson: String): Result<Boolean> {
        val recoveryCode = Adapters.recoveryCodeAdapter.fromJson(recoveryCodeRawJson)?.recovery ?: return Result.Error(reason = "Failed reading json")
        val primaryKey = recoveryCode.primaryKey
        val userId = recoveryCode.userId
        val deviceId = syncDeviceIds.deviceId()
        val deviceName = syncDeviceIds.deviceName()

        return performLogin(userId, deviceId, deviceName, primaryKey)
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

    @Deprecated("Method only used for testing purposes.", level = WARNING)
    override fun storeRecoveryCode() {
        val primaryKey = syncStore.primaryKey ?: return
        val userID = syncStore.userId ?: return
        val recoveryCodeJson = Adapters.recoveryCodeAdapter.toJson(LinkCode(RecoveryCode(primaryKey, userID)))

        Timber.i("SYNC store recoverCode: $recoveryCodeJson")
        syncStore.recoveryCode = recoveryCodeJson
    }

    override fun getRecoveryCode(): String? {
        val primaryKey = syncStore.primaryKey ?: return null
        val userID = syncStore.userId ?: return null
        return Adapters.recoveryCodeAdapter.toJson(LinkCode(RecoveryCode(primaryKey, userID)))
    }

    override fun getLinkingQR(): String {
        val prepareForConnect = nativeLib.prepareForConnect()
        val deviceId = syncDeviceIds.deviceId()
        return Adapters.connectCodeAdapter.toJson(ConnectCode(prepareForConnect.publicKey, deviceId))
    }

    override fun removeAccount() {
        syncStore.clearAll(keepRecoveryCode = false)
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
                Timber.i("SYNC logout failed $result")
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
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() } ?: return Result.Error(reason = "Token Empty")

        return when (val result = syncApi.deleteAccount(token)) {
            is Result.Error -> {
                Timber.i("SYNC deleteAccount failed $result")
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

    override fun getConnectedDevices(): Result<List<ConnectedDevice>> {
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: return Result.Error(reason = "Token Empty")

        return when (val result = syncApi.getDevices(token)) {
            is Result.Error -> {
                Timber.i("SYNC getDevices failed $result")
                result
            }

            is Result.Success -> {
                return Result.Success(
                    result.data.map {
                        ConnectedDevice(
                            thisDevice = syncStore.deviceId == it.device_id,
                            deviceName = it.device_name,
                            deviceId = it.device_id,
                        )
                    },
                )
            }
        }
    }

    private fun isSignedIn() = !syncStore.primaryKey.isNullOrEmpty()

    private fun performLogin(
        userId: String,
        deviceId: String,
        deviceName: String,
        primaryKey: String,
    ): Result<Boolean> {
        val preLogin: LoginKeys = nativeLib.prepareForLogin(primaryKey)
        if (preLogin.result != 0L) return Result.Error(code = preLogin.result.toInt(), reason = "Login account keys failed")

        val result = syncApi.login(
            userID = userId,
            hashedPassword = preLogin.passwordHash,
            deviceId = deviceId,
            deviceName = deviceName,
        )

        return when (result) {
            is Result.Error -> {
                result
            }

            is Result.Success -> {
                val decryptResult = nativeLib.decrypt(result.data.protected_encryption_key, preLogin.stretchedPrimaryKey)
                if (decryptResult.result != 0L) return Result.Error(code = decryptResult.result.toInt(), reason = "Decrypt failed")
                syncStore.userId = userId
                syncStore.deviceId = deviceId
                syncStore.deviceName = deviceName
                syncStore.token = result.data.token
                syncStore.primaryKey = preLogin.primaryKey
                syncStore.secretKey = decryptResult.decryptedData
                Result.Success(true)
            }
        }
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().build()
            val recoveryCodeAdapter: JsonAdapter<LinkCode> = moshi.adapter(LinkCode::class.java)

            val connectCodeAdapter: JsonAdapter<ConnectCode> =
                moshi.adapter(ConnectCode::class.java)
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
    val recovery: RecoveryCode,
)

data class RecoveryCode(
    @field:Json(name = "primary_key") val primaryKey: String,
    @field:Json(name = "user_id") val userId: String,
)

data class ConnectedDevice(
    val thisDevice: Boolean = false,
    val deviceName: String,
    val deviceId: String,
)

data class ConnectCode(
    val publicKey: String,
    val deviceId: String,
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
