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
import com.duckduckgo.sync.impl.API_CODE.GONE
import com.duckduckgo.sync.impl.API_CODE.NOT_FOUND
import com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN
import com.duckduckgo.sync.impl.AccountErrorCodes.CONNECT_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.CREATE_ACCOUNT_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.GENERIC_ERROR
import com.duckduckgo.sync.impl.AccountErrorCodes.INVALID_CODE
import com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.pixels.*
import com.duckduckgo.sync.store.*
import com.squareup.anvil.annotations.*
import com.squareup.moshi.*
import dagger.*
import javax.inject.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

interface SyncAccountRepository {

    fun isSyncSupported(): Boolean
    fun createAccount(): Result<Boolean>
    fun isSignedIn(): Boolean
    fun processCode(stringCode: String): Result<Boolean>
    fun getAccountInfo(): AccountInfo
    fun logout(deviceId: String): Result<Boolean>
    fun deleteAccount(): Result<Boolean>
    fun latestToken(): String
    fun getRecoveryCode(): Result<String>
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

    override fun isSyncSupported(): Boolean {
        return syncStore.isEncryptionSupported()
    }

    override fun createAccount(): Result<Boolean> {
        if (isSignedIn()) {
            return Error(code = ALREADY_SIGNED_IN.code, reason = "Already signed in")
                .alsoFireAlreadySignedInErrorPixel()
        }
        return performCreateAccount().onFailure {
            it.alsoFireSignUpErrorPixel()
            return it.copy(code = CREATE_ACCOUNT_FAILED.code)
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

        return Error(code = INVALID_CODE.code, reason = "Failed to decode recovery code")
    }

    private fun login(recoveryCode: RecoveryCode): Result<Boolean> {
        if (isSignedIn()) {
            return Error(code = ALREADY_SIGNED_IN.code, reason = "Already signed in")
                .alsoFireAlreadySignedInErrorPixel()
        }

        val primaryKey = recoveryCode.primaryKey
        val userId = recoveryCode.userId
        val deviceId = syncDeviceIds.deviceId()
        val deviceName = syncDeviceIds.deviceName()

        return performLogin(userId, deviceId, deviceName, primaryKey).onFailure {
            it.alsoFireLoginErrorPixel()
            return it.copy(code = LOGIN_FAILED.code)
        }
    }

    override fun renameDevice(device: ConnectedDevice): Result<Boolean> {
        val userId = syncStore.userId ?: return Error(reason = "Rename Device: Not existing userId").alsoFireUpdateDeviceErrorPixel()
        val primaryKey = syncStore.primaryKey ?: return Error(reason = "Rename Device: Not existing primaryKey").alsoFireUpdateDeviceErrorPixel()
        return performLogin(userId, device.deviceId, device.deviceName, primaryKey).onFailure {
            it.alsoFireUpdateDeviceErrorPixel()
            return it.copy(code = LOGIN_FAILED.code)
        }
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

    override fun getRecoveryCode(): Result<String> {
        val primaryKey = syncStore.primaryKey ?: return Error(reason = "Get Recovery Code: Not existing primary Key").alsoFireAccountErrorPixel()
        val userID = syncStore.userId ?: return Error(reason = "Get Recovery Code: Not existing userId").alsoFireAccountErrorPixel()
        return Result.Success(Adapters.recoveryCodeAdapter.toJson(LinkCode(RecoveryCode(primaryKey, userID))).encodeB64())
    }

    override fun getConnectQR(): Result<String> {
        val prepareForConnect = kotlin.runCatching {
            nativeLib.prepareForConnect().also {
                it.checkResult("Creating ConnectQR code failed")
            }
        }.getOrElse { return it.asErrorResult().alsoFireAccountErrorPixel().copy(code = GENERIC_ERROR.code) }

        val deviceId = syncDeviceIds.deviceId()
        syncStore.deviceId = deviceId
        syncStore.primaryKey = prepareForConnect.publicKey
        syncStore.secretKey = prepareForConnect.secretKey

        val linkingQRCode = Adapters.recoveryCodeAdapter.toJson(
            LinkCode(connect = ConnectCode(deviceId = deviceId, secretKey = prepareForConnect.publicKey)),
        ) ?: return Error(reason = "Error generating Linking Code").alsoFireAccountErrorPixel()

        return Result.Success(linkingQRCode.encodeB64())
    }

    private fun connectDevice(connectKeys: ConnectCode): Result<Boolean> {
        if (!isSignedIn()) {
            performCreateAccount().onFailure {
                it.alsoFireSignUpErrorPixel()
                return it.copy(code = CREATE_ACCOUNT_FAILED.code)
            }
        }

        return performConnect(connectKeys).onFailure {
            it.alsoFireLoginErrorPixel()
            return it.copy(code = CONNECT_FAILED.code)
        }
    }

    override fun pollConnectionKeys(): Result<Boolean> {
        val deviceId = syncDeviceIds.deviceId()

        val result = syncApi.connectDevice(deviceId)
        return when (result) {
            is Error -> {
                if (result.code == NOT_FOUND.code) {
                    return Result.Success(false)
                } else if (result.code == GONE.code) {
                    return Error(code = CONNECT_FAILED.code, reason = "Connect: keys expired").alsoFireAccountErrorPixel()
                }
                result.alsoFireAccountErrorPixel()
            }

            is Result.Success -> {
                val sealOpen = kotlin.runCatching {
                    nativeLib.sealOpen(result.data, syncStore.primaryKey!!, syncStore.secretKey!!)
                }.getOrElse { throwable ->
                    throwable.asErrorResult().alsoFireAccountErrorPixel()
                    return Error(code = CONNECT_FAILED.code, reason = "Connect: Error opening seal")
                }
                val recoveryCode = Adapters.recoveryCodeAdapter.fromJson(sealOpen)?.recovery
                    ?: return Error(code = CONNECT_FAILED.code, reason = "Connect: Error reading received recovery code").alsoFireAccountErrorPixel()
                syncStore.userId = recoveryCode.userId
                syncStore.primaryKey = recoveryCode.primaryKey
                return performLogin(recoveryCode.userId, deviceId, syncDeviceIds.deviceName(), recoveryCode.primaryKey).onFailure {
                    return it.copy(code = LOGIN_FAILED.code)
                }
            }
        }
    }

    override fun logout(deviceId: String): Result<Boolean> {
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: return Error(reason = "Logout: Token Empty").alsoFireLogoutErrorPixel()

        val logoutThisDevice = deviceId.isEmpty() || deviceId == syncStore.deviceId

        val deviceId = if (logoutThisDevice) {
            syncStore.deviceId.takeUnless { it.isNullOrEmpty() }
                ?: return Error(reason = "Logout: Device Id Empty").alsoFireLogoutErrorPixel()
        } else {
            deviceId
        }

        return when (val result = syncApi.logout(token, deviceId)) {
            is Error -> {
                if (logoutThisDevice) {
                    result.alsoFireLogoutErrorPixel()
                } else {
                    result.alsoFireRemoveDeviceErrorPixel()
                }
                result.copy(code = GENERIC_ERROR.code)
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
        val token = syncStore.token.takeUnless {
            it.isNullOrEmpty()
        } ?: return Error(reason = "Delete account: Token Empty").alsoFireDeleteAccountErrorPixel()

        return when (val result = syncApi.deleteAccount(token)) {
            is Error -> {
                result.alsoFireDeleteAccountErrorPixel().copy(code = GENERIC_ERROR.code)
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
                result.alsoFireAccountErrorPixel().copy(code = GENERIC_ERROR.code)
            }

            is Result.Success -> {
                return Result.Success(
                    result.data.mapNotNull { device ->
                        try {
                            val decryptedDeviceName = nativeLib.decryptData(device.deviceName, primaryKey).decryptedData
                            val decryptedDeviceType = device.deviceType.takeUnless { it.isNullOrEmpty() }?.let { encryptedDeviceType ->
                                DeviceType(nativeLib.decryptData(encryptedDeviceType, primaryKey).decryptedData)
                            } ?: DeviceType()

                            ConnectedDevice(
                                thisDevice = syncStore.deviceId == device.deviceId,
                                deviceName = decryptedDeviceName,
                                deviceId = device.deviceId,
                                deviceType = decryptedDeviceType,
                            )
                        } catch (throwable: Throwable) {
                            throwable.asErrorResult().alsoFireAccountErrorPixel()
                            logout(device.deviceId)
                            null
                        }
                    }.sortedWith { a, b ->
                        if (a.thisDevice) -1 else 1
                    },
                )
            }
        }
    }

    override fun isSignedIn() = syncStore.isSignedIn()

    private fun performCreateAccount(): Result<Boolean> {
        val userId = syncDeviceIds.userId()
        val account: AccountKeys = kotlin.runCatching {
            nativeLib.generateAccountKeys(userId = userId).also {
                it.checkResult("Create Account: keys failed")
            }
        }.getOrElse { throwable ->
            return throwable.asErrorResult()
        }

        val deviceId = syncDeviceIds.deviceId()
        val deviceName = syncDeviceIds.deviceName()
        val deviceType = syncDeviceIds.deviceType()

        val encryptedDeviceName = kotlin.runCatching {
            nativeLib.encryptData(deviceName, account.primaryKey).also {
                it.checkResult("Create Account: Encrypting device name failed")
            }.encryptedData
        }.getOrElse { throwable ->
            return throwable.asErrorResult()
        }

        val encryptedDeviceType = kotlin.runCatching {
            nativeLib.encryptData(deviceType.deviceFactor, account.primaryKey).also {
                it.checkResult("Create Account: Encrypting device factor failed")
            }.encryptedData
        }.getOrElse { throwable ->
            return throwable.asErrorResult()
        }

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

    private fun performLogin(
        userId: String,
        deviceId: String,
        deviceName: String,
        primaryKey: String,
    ): Result<Boolean> {
        val preLogin: LoginKeys = kotlin.runCatching {
            nativeLib.prepareForLogin(primaryKey).also {
                it.checkResult("Login: prepareForLogin failed")
            }
        }.getOrElse { throwable ->
            return throwable.asErrorResult()
        }

        val deviceType = syncDeviceIds.deviceType()
        val encryptedDeviceType = kotlin.runCatching {
            nativeLib.encryptData(deviceType.deviceFactor, preLogin.primaryKey).encryptedData
        }.getOrElse { throwable ->
            return throwable.asErrorResult()
        }

        val result = syncApi.login(
            userID = userId,
            hashedPassword = preLogin.passwordHash,
            deviceId = deviceId,
            deviceName = nativeLib.encryptData(deviceName, preLogin.primaryKey).encryptedData,
            deviceType = encryptedDeviceType,
        )

        return when (result) {
            is Error -> {
                result
            }

            is Result.Success -> {
                val decryptResult = kotlin.runCatching {
                    nativeLib.decrypt(result.data.protected_encryption_key, preLogin.stretchedPrimaryKey).also {
                        it.checkResult("Login: decrypt protection keys failed")
                    }
                }.getOrElse { throwable ->
                    return throwable.asErrorResult()
                }

                syncStore.storeCredentials(userId, deviceId, deviceName, preLogin.primaryKey, decryptResult.decryptedData, result.data.token)
                appCoroutineScope.launch(dispatcherProvider.io()) {
                    syncEngine.triggerSync(ACCOUNT_LOGIN)
                }

                Result.Success(true)
            }
        }
    }

    private fun performConnect(connectKeys: ConnectCode): Result<Boolean> {
        val primaryKey = syncStore.primaryKey ?: return Error(reason = "Connect Device: Error reading PK")
        val userId = syncStore.userId ?: return Error(reason = "Connect Device: Error reading UserId")
        val token = syncStore.token ?: return Error(reason = "Connect Device: Error token")
        val recoverKey = Adapters.recoveryCodeAdapter.toJson(LinkCode(RecoveryCode(primaryKey = primaryKey, userId = userId)))
        val seal = kotlin.runCatching {
            nativeLib.seal(recoverKey, connectKeys.secretKey)
        }.getOrElse { throwable ->
            return throwable.asErrorResult()
        }

        return syncApi.connect(token = token, deviceId = connectKeys.deviceId, publicKey = seal)
    }

    private fun Error.alsoFireSignUpErrorPixel(): Error {
        syncPixels.fireSyncAccountErrorPixel(this, SyncAccountOperation.SIGNUP)
        return this
    }

    private fun Error.alsoFireLoginErrorPixel(): Error {
        syncPixels.fireSyncAccountErrorPixel(this, SyncAccountOperation.LOGIN)
        return this
    }

    private fun Error.alsoFireLogoutErrorPixel(): Error {
        syncPixels.fireSyncAccountErrorPixel(this, SyncAccountOperation.LOGOUT)
        return this
    }

    private fun Error.alsoFireAccountErrorPixel(): Error {
        syncPixels.fireSyncAccountErrorPixel(this, SyncAccountOperation.GENERIC)
        return this
    }

    private fun Error.alsoFireUpdateDeviceErrorPixel(): Error {
        syncPixels.fireSyncAccountErrorPixel(this, SyncAccountOperation.UPDATE_DEVICE)
        return this
    }

    private fun Error.alsoFireRemoveDeviceErrorPixel(): Error {
        syncPixels.fireSyncAccountErrorPixel(this, SyncAccountOperation.REMOVE_DEVICE)
        return this
    }

    private fun Error.alsoFireDeleteAccountErrorPixel(): Error {
        syncPixels.fireSyncAccountErrorPixel(this, SyncAccountOperation.DELETE_ACCOUNT)
        return this
    }

    private fun Error.alsoFireAlreadySignedInErrorPixel(): Error {
        syncPixels.fireSyncAccountErrorPixel(this, SyncAccountOperation.USER_SIGNED_IN)
        return this
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().build()
            val recoveryCodeAdapter: JsonAdapter<LinkCode> = moshi.adapter(LinkCode::class.java)
        }
    }
}

private fun SyncCryptoResult.checkResult(errorMessage: String) {
    if (result != 0) {
        throw SyncAccountException(this.result, errorMessage)
    }
}

private fun Throwable.asErrorResult(): Error {
    return when (this) {
        is SyncAccountException -> Error(code = this.code, reason = this.message.toString())
        else -> Error(reason = this.message.toString())
    }
}

private class SyncAccountException(
    val code: Int,
    message: String,
) : Exception(message)

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

enum class AccountErrorCodes(val code: Int) {
    GENERIC_ERROR(-1),
    ALREADY_SIGNED_IN(51),
    LOGIN_FAILED(52),
    CREATE_ACCOUNT_FAILED(53),
    CONNECT_FAILED(54),
    INVALID_CODE(55),
}

sealed class Result<out R> {

    data class Success<out T>(val data: T) : Result<T>()
    data class Error(
        val code: Int = GENERIC_ERROR.code,
        val reason: String = "",
    ) : Result<Nothing>()

    override fun toString(): String {
        return when (this) {
            is Success<*> -> "Success[data=$data]"
            is Error -> "Error[exception=$code, $reason]"
        }
    }
}

fun <T> Result<T>.getOrNull(): T? {
    return when (this) {
        is Result.Success -> data
        is Result.Error -> null
    }
}

inline fun <T> Result<T>.onSuccess(action: (value: T) -> Unit): Result<T> {
    if (this is Result.Success) {
        action(data)
    }

    return this
}

inline fun <T> Result<T>.onFailure(action: (error: Result.Error) -> Unit): Result<T> {
    if (this is Result.Error) {
        action(this)
    }

    return this
}
