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
import com.duckduckgo.sync.impl.CodeType.UNKNOWN
import com.duckduckgo.sync.impl.ExchangeResult.*
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.pixels.*
import com.duckduckgo.sync.store.*
import com.squareup.anvil.annotations.*
import com.squareup.moshi.*
import dagger.*
import java.util.UUID
import javax.inject.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

interface SyncAccountRepository {

    fun getCodeType(stringCode: String): CodeType
    fun isSyncSupported(): Boolean
    fun createAccount(): Result<Boolean>
    fun isSignedIn(): Boolean
    fun processCode(stringCode: String): Result<Boolean>
    fun getAccountInfo(): AccountInfo
    fun logout(deviceId: String): Result<Boolean>
    fun deleteAccount(): Result<Boolean>
    fun latestToken(): String
    fun getRecoveryCode(): Result<String>
    fun getInvitationCode(): Result<String>
    fun getThisConnectedDevice(): ConnectedDevice?
    fun getConnectedDevices(): Result<List<ConnectedDevice>>
    fun getConnectQR(): Result<String>
    fun pollConnectionKeys(): Result<Boolean>
    fun pollSecondDeviceAck(): Result<Boolean>
    fun renameDevice(device: ConnectedDevice): Result<Boolean>
    fun logoutAndJoinNewAccount(stringCode: String): Result<Boolean>
    fun pollForRecoveryCodeAndLogin(): Result<ExchangeResult>
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
    private val syncFeature: SyncFeature,
) : SyncAccountRepository {

    private var pendingInvitationAsInviter: PendingInvitation? = null
    private var pendingInvitationAsReceiver: PendingInvitation? = null

    private val connectedDevicesCached: MutableList<ConnectedDevice> = mutableListOf()

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
        Timber.v("processing code: $stringCode")
        val decodedCode: String? = kotlin.runCatching {
            return@runCatching stringCode.decodeB64()
        }.getOrNull()
        if (decodedCode == null) {
            Timber.w("Failed while b64 decoding barcode; barcode is unusable")
            return Error(code = INVALID_CODE.code, reason = "Invalid barcode")
        }
        Timber.v("cdr code is $decodedCode")

        kotlin.runCatching {
            Adapters.recoveryCodeAdapter.fromJson(decodedCode)?.recovery
        }.getOrNull()?.let {
            Timber.d("cdr code is a recovery code")
            return login(it)
        }
        Timber.d("cdr code is not a recovery code")

        kotlin.runCatching {
            Adapters.recoveryCodeAdapter.fromJson(decodedCode)?.connect
        }.getOrNull()?.let {
            Timber.d("cdr code is a connect code")
            return connectDevice(it)
        }
        Timber.d("cdr code is not a connect code")

        kotlin.runCatching {
            Adapters.invitationCodeAdapter.fromJson(decodedCode)?.exchangeKey
        }.getOrNull()?.let {
            if (!syncFeature.exchangeKeysToSyncWithAnotherDevice().isEnabled()) {
                Timber.w("Scanned exchange code type but exchanging keys to sync with another device is disabled")
                return@let null
            }

            Timber.d("cdr code is an invitation code")
            // CDR-InviteFlow - B (https://app.asana.com/0/72649045549333/1209571867429615)
            Timber.e("cdr-InviteFlow B")
            return completeExchange(it)
        }
        Timber.d("cdr code is not an invitation code or invitations not supported")

        return Error(code = INVALID_CODE.code, reason = "Failed to decode recovery code")
    }

    override fun getCodeType(stringCode: String): CodeType {
        return kotlin.runCatching {
            val decodedCode = stringCode.decodeB64()
            when {
                Adapters.recoveryCodeAdapter.fromJson(decodedCode)?.recovery != null -> CodeType.RECOVERY
                Adapters.recoveryCodeAdapter.fromJson(decodedCode)?.connect != null -> CodeType.CONNECT
                Adapters.invitationCodeAdapter.fromJson(decodedCode) != null -> CodeType.EXCHANGE
                else -> UNKNOWN
            }
        }.onFailure {
            Timber.e(it, "Failed to decode code")
        }.getOrDefault(UNKNOWN)
    }

    private fun completeExchange(invitationCode: InvitationCode): Result<Boolean> {
        Timber.v("cdr invitation code is $invitationCode")

        // generate new ID and and public/private key-pair
        val thisDeviceKeyId = UUID.randomUUID().toString()
        val thisDeviceKeyPair = nativeLib.prepareForConnect()

        pendingInvitationAsReceiver = PendingInvitation(
            keyId = thisDeviceKeyId,
            privateKey = thisDeviceKeyPair.secretKey,
            publicKey = thisDeviceKeyPair.publicKey,
        )
        Timber.w("cdr this device's key ID is $thisDeviceKeyId")
        Timber.w("cdr this device's public key is ${thisDeviceKeyPair.publicKey}")

        val invitedDeviceDetails = InvitedDeviceDetails(
            keyId = thisDeviceKeyId,
            publicKey = thisDeviceKeyPair.publicKey,
            deviceName = "TODO-fill in device name",
        )
        val payload = Adapters.invitedDeviceAdapter.toJson(invitedDeviceDetails)
            .also { Timber.d("cdr invitation acceptable payload is $it") }

        val encrypted = nativeLib.seal(payload, invitationCode.publicKey)
        return syncApi.acceptInvitation(invitationCode.keyId, encrypted)
    }

    // CDR-InviteFlow - E (https://app.asana.com/0/72649045549333/1209571867429615)
    override fun pollForRecoveryCodeAndLogin(): Result<ExchangeResult> {
        Timber.e("cdr-InviteFlow E")

        val pendingInvite = pendingInvitationAsReceiver
            ?: return Error(code = CONNECT_FAILED.code, reason = "Connect: No pending invite initialized")

        return when (val result = syncApi.getExchange(pendingInvite.keyId)) {
            is Error -> {
                Timber.e("cdr failed to complete exchange. error code: ${result.code} ${result.reason}")
                if (result.code == NOT_FOUND.code) {
                    return Success(Pending)
                } else if (result.code == GONE.code) {
                    return Error(code = CONNECT_FAILED.code, reason = "Connect: keys expired").alsoFireAccountErrorPixel()
                }
                result.alsoFireAccountErrorPixel()
            }

            is Success -> {
                Timber.v("cdr received encrypted recovery code")

                val decryptedJson = kotlin.runCatching {
                    nativeLib.sealOpen(result.data, pendingInvite.publicKey, pendingInvite.privateKey)
                }.getOrElse { throwable ->
                    throwable.asErrorResult().alsoFireAccountErrorPixel()
                    return Error(code = CONNECT_FAILED.code, reason = "Connect: Error opening seal")
                }

                Timber.w("cdr got recovery code JSON: $decryptedJson")

                val recoveryData = Adapters.recoveryCodeAdapter.fromJson(decryptedJson)?.recovery
                    ?: return Error(code = GENERIC_ERROR.code, reason = "Connect: Error reading recovery code").alsoFireAccountErrorPixel()

                return when (val loginResult = login(recoveryData)) {
                    is Success -> Success(LoggedIn)
                    is Error -> {
                        return if (loginResult.code == ALREADY_SIGNED_IN.code) {
                            Success(AccountSwitchingRequired(decryptedJson.encodeB64()))
                        } else {
                            loginResult
                        }
                    }
                }
            }
        }
    }

    private fun login(recoveryCode: RecoveryCode): Result<Boolean> {
        Timber.w("cdr attempting to login now recovery code is available $recoveryCode")
        var wasUserLogout = false
        if (isSignedIn()) {
            val allowSwitchAccount = syncFeature.seamlessAccountSwitching().isEnabled()
            val error = Error(code = ALREADY_SIGNED_IN.code, reason = "Already signed in").alsoFireAlreadySignedInErrorPixel()
            if (allowSwitchAccount && connectedDevicesCached.size == 1) {
                val thisDeviceId = syncStore.deviceId.orEmpty()
                val result = logout(thisDeviceId)
                if (result is Error) {
                    return result
                }
                wasUserLogout = true
            } else {
                return error
            }
        }

        val primaryKey = recoveryCode.primaryKey
        val userId = recoveryCode.userId
        val deviceId = syncDeviceIds.deviceId()
        val deviceName = syncDeviceIds.deviceName()

        return performLogin(userId, deviceId, deviceName, primaryKey).onFailure {
            it.alsoFireLoginErrorPixel()
            if (wasUserLogout) {
                syncPixels.fireUserSwitchedLoginError()
            }
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
        return Success(Adapters.recoveryCodeAdapter.toJson(LinkCode(RecoveryCode(primaryKey, userID))).encodeB64())
    }

    override fun getInvitationCode(): Result<String> {
        Timber.d("InvitationFlow: Generating invitation code")

        // generate new ID and and public/private key-pair
        val keyId = UUID.randomUUID().toString()
        val prepareForConnect = nativeLib.prepareForConnect()

        val pendingInvitation = PendingInvitation(
            keyId = keyId,
            privateKey = prepareForConnect.secretKey,
            publicKey = prepareForConnect.publicKey,
        ).also {
            pendingInvitationAsInviter = it
            Timber.w("cdr this device's key ID is $keyId")
            Timber.w("cdr this device's public key is ${it.publicKey}")
        }

        val invitationCode = InvitationCode(keyId = pendingInvitation.keyId, publicKey = prepareForConnect.publicKey)
        val invitationWrapper = InvitationCodeWrapper(invitationCode)
        val code = Adapters.invitationCodeAdapter.toJson(invitationWrapper).encodeB64()
        return Success(code).also {
            Timber.v("cdr invitation code is $code")
        }
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

        return Success(linkingQRCode.encodeB64())
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

        return when (val result = syncApi.connectDevice(deviceId)) {
            is Error -> {
                if (result.code == NOT_FOUND.code) {
                    return Success(false)
                } else if (result.code == GONE.code) {
                    return Error(code = CONNECT_FAILED.code, reason = "Connect: keys expired").alsoFireAccountErrorPixel()
                }
                result.alsoFireAccountErrorPixel()
            }

            is Success -> {
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

    // TODO: review error codes
    // CDR-InviteFlow - C (https://app.asana.com/0/72649045549333/1209571867429615)
    override fun pollSecondDeviceAck(): Result<Boolean> {
        Timber.e("cdr-InviteFlow C")
        val keyId = pendingInvitationAsInviter?.keyId ?: return Error(reason = "No pending invitation initialized")

        return when (val result = syncApi.getExchange(keyId)) {
            is Error -> {
                if (result.code == NOT_FOUND.code) { // TODO: check with JP which errors we can receive here.
                    return Success(false)
                } else if (result.code == GONE.code) {
                    return Error(
                        code = CONNECT_FAILED.code,
                        reason = "Connect: keys expired",
                    ) // .alsoFireAccountErrorPixel() // TODO: change according to JP errors
                }
                result.alsoFireAccountErrorPixel()
            }

            is Success -> {
                Timber.v("cdr Found invitation acceptance for keyId: $keyId} ${result.data}")

                val decrypted = kotlin.runCatching {
                    val pending = pendingInvitationAsInviter
                    if (pending == null) {
                        IllegalStateException("cdr No pending invitation initialized").asErrorResult().alsoFireAccountErrorPixel()
                        return Error(code = CONNECT_FAILED.code, reason = "Connect: Error opening seal")
                    }
                    nativeLib.sealOpen(result.data, pending.publicKey, pending.privateKey)
                }.getOrElse { throwable ->
                    throwable.asErrorResult().alsoFireAccountErrorPixel()
                    return Error(code = CONNECT_FAILED.code, reason = "Connect: Error opening seal")
                }

                Timber.v("cdr invitation acceptance received: $decrypted")

                val response = Adapters.invitedDeviceAdapter.fromJson(decrypted)
                    ?: return Error(code = GENERIC_ERROR.code, reason = "Connect: Error reading invitation response").alsoFireAccountErrorPixel()

                val otherDevicePublicKey = response.publicKey
                val otherDeviceKeyId = response.keyId

                Timber.v(
                    "cdr We have received the other device's details. " +
                        "name:${response.deviceName}, keyId:${response.keyId}, public key: ${response.publicKey}",
                )

                // we encrypt our secrets with otherDevicePublicKey
                // we send them to the BE endpoint
                return sendSecrets(otherDeviceKeyId, otherDevicePublicKey).onFailure {
                    Timber.e("cdr failed to send secrets. error code: ${it.code} ${it.reason}")
                    return it.copy(code = LOGIN_FAILED.code)
                }
            }
        }
    }

    // CDR-InviteFlow - D (https://app.asana.com/0/72649045549333/1209571867429615)
    private fun sendSecrets(keyId: String, publicKey: String): Result<Boolean> {
        Timber.e("cdr-InviteFlow D")
        when (val recoveryCode = getRecoveryCode()) {
            is Error -> {
                Timber.e("cdr failed to get recovery code. error code: ${recoveryCode.code} ${recoveryCode.reason}")
                return Error(code = GENERIC_ERROR.code, reason = "Connect: Error getting recovery code") // .alsoFireAccountErrorPixel()
            }
            is Success -> {
                Timber.v(
                    "cdr got recovery code, ready to share. ${recoveryCode.data} ${recoveryCode.data.decodeB64()} " +
                        "will be encrypted with public key $publicKey",
                )
                // recovery code comes b64 encoded, so we need to decode it, then encrypt, which automatically b64 encodes the encrypted form
                val json = recoveryCode.data.decodeB64()
                val encryptedJson = nativeLib.seal(json, publicKey)
                return syncApi.sendSecrets(keyId, encryptedJson)
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

            is Success -> {
                if (logoutThisDevice) {
                    syncStore.clearAll()
                }
                Success(true)
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

            is Success -> {
                syncStore.clearAll()
                Success(true)
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
                connectedDevicesCached.clear()
                result.alsoFireAccountErrorPixel().copy(code = GENERIC_ERROR.code)
            }

            is Success -> {
                return Success(
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
                            if (syncStore.deviceId != device.deviceId) {
                                logout(device.deviceId)
                            }
                            null
                        }
                    }.sortedWith { a, b ->
                        if (a.thisDevice) -1 else 1
                    }.also {
                        connectedDevicesCached.apply {
                            clear()
                            addAll(it)
                        }
                    },
                )
            }
        }
    }

    override fun isSignedIn() = syncStore.isSignedIn()

    override fun logoutAndJoinNewAccount(stringCode: String): Result<Boolean> {
        val thisDeviceId = syncStore.deviceId.orEmpty()
        return when (val result = logout(thisDeviceId)) {
            is Error -> {
                syncPixels.fireUserSwitchedLogoutError()
                result
            }

            is Success -> {
                val loginResult = processCode(stringCode)
                if (loginResult is Error) {
                    syncPixels.fireUserSwitchedLoginError()
                }
                Success(true)
            }
        }
    }

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

            is Success -> {
                syncStore.storeCredentials(account.userId, deviceId, deviceName, account.primaryKey, account.secretKey, result.data.token)
                syncEngine.triggerSync(ACCOUNT_CREATION)
                Timber.d("Sync-Account: recovery code is ${getRecoveryCode()}")
                Success(true)
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

            is Success -> {
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

                Success(true)
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

            val invitationCodeAdapter: JsonAdapter<InvitationCodeWrapper> = moshi.adapter(InvitationCodeWrapper::class.java)
            val invitedDeviceAdapter: JsonAdapter<InvitedDeviceDetails> = moshi.adapter(InvitedDeviceDetails::class.java)
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

data class InvitationCodeWrapper(
    @field:Json(name = "exchange_key") val exchangeKey: InvitationCode,
)

data class InvitationCode(
    @field:Json(name = "key_id") val keyId: String,
    @field:Json(name = "public_key") val publicKey: String,
)

data class InvitedDeviceDetails(
    @field:Json(name = "key_id") val keyId: String,
    @field:Json(name = "public_key") val publicKey: String,
    @field:Json(name = "device_name") val deviceName: String,
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

enum class CodeType {
    RECOVERY,
    CONNECT,
    EXCHANGE,
    UNKNOWN,
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
        is Success -> data
        is Error -> null
    }
}

inline fun <T> Result<T>.onSuccess(action: (value: T) -> Unit): Result<T> {
    if (this is Success) {
        action(data)
    }

    return this
}

inline fun <T> Result<T>.onFailure(action: (error: Error) -> Unit): Result<T> {
    if (this is Error) {
        action(this)
    }

    return this
}

private data class PendingInvitation(
    val keyId: String,
    val privateKey: String,
    var publicKey: String,
)

sealed interface ExchangeResult {
    data object LoggedIn : ExchangeResult
    data object Pending : ExchangeResult
    data class AccountSwitchingRequired(val recoveryCode: String) : ExchangeResult
}
