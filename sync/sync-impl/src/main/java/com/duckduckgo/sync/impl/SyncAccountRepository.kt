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
import com.duckduckgo.sync.impl.AccountErrorCodes.EXCHANGE_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.GENERIC_ERROR
import com.duckduckgo.sync.impl.AccountErrorCodes.INVALID_CODE
import com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED
import com.duckduckgo.sync.impl.ExchangeResult.*
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository.AuthCode
import com.duckduckgo.sync.impl.SyncAuthCode.Connect
import com.duckduckgo.sync.impl.SyncAuthCode.Exchange
import com.duckduckgo.sync.impl.SyncAuthCode.Recovery
import com.duckduckgo.sync.impl.SyncAuthCode.Unknown
import com.duckduckgo.sync.impl.pixels.*
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrlWrapper
import com.duckduckgo.sync.store.*
import com.squareup.anvil.annotations.*
import com.squareup.moshi.*
import dagger.*
import java.util.UUID
import javax.inject.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat

interface SyncAccountRepository {

    fun parseSyncAuthCode(stringCode: String): SyncAuthCode
    fun isSyncSupported(): Boolean
    fun createAccount(): Result<Boolean>
    fun isSignedIn(): Boolean
    fun processCode(code: SyncAuthCode): Result<Boolean>
    fun getAccountInfo(): AccountInfo
    fun logout(deviceId: String): Result<Boolean>
    fun deleteAccount(): Result<Boolean>
    fun latestToken(): String
    fun getRecoveryCode(): Result<AuthCode>
    fun getThisConnectedDevice(): ConnectedDevice?
    fun getConnectedDevices(): Result<List<ConnectedDevice>>
    fun getConnectQR(): Result<AuthCode>
    fun pollConnectionKeys(): Result<Boolean>
    fun generateExchangeInvitationCode(): Result<AuthCode>
    fun pollSecondDeviceExchangeAcknowledgement(): Result<Boolean>
    fun pollForRecoveryCodeAndLogin(): Result<ExchangeResult>
    fun renameDevice(device: ConnectedDevice): Result<Boolean>
    fun logoutAndJoinNewAccount(stringCode: String): Result<Boolean>

    data class AuthCode(
        /**
         * A code that is suitable for displaying in a QR code.
         */
        val qrCode: String,

        /**
         * Just the code (b64-encoded)
         */
        val rawCode: String,
    )
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
    private val deviceKeyGenerator: DeviceKeyGenerator,
    private val syncCodeUrlWrapper: SyncBarcodeUrlWrapper,
) : SyncAccountRepository {

    /**
     * If there is a key-exchange flow in progress, we need to keep a reference to them
     * There are separate device details for the inviter and the receiver
     *
     * Inviter is reset every time a new exchange invitation code is created.
     * Receiver is reset every time an exchange invitation is received.
     */
    private var exchangeDeviceDetailsAsInviter: DeviceDetailsForKeyExchange? = null
    private var exchangeDeviceDetailsAsReceiver: DeviceDetailsForKeyExchange? = null

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

    override fun processCode(code: SyncAuthCode): Result<Boolean> {
        when (code) {
            is Recovery -> {
                logcat { "Sync: code is a recovery code" }
                return login(code.b64Code)
            }

            is Connect -> {
                logcat { "Sync: code is a connect code" }
                return connectDevice(code.b64Code)
            }

            is Exchange -> {
                if (!syncFeature.exchangeKeysToSyncWithAnotherDevice().isEnabled()) {
                    logcat(WARN) { "Sync: Scanned exchange code type but exchanging keys to sync with another device is disabled" }
                } else {
                    return onInvitationCodeReceived(code.b64Code)
                }
            }

            else -> {
                logcat { "Sync: code type unknown" }
            }
        }
        logcat(ERROR) { "Sync: code type (${code.javaClass.simpleName}) is not supported" }
        return Error(code = INVALID_CODE.code, reason = "Failed to decode code")
    }

    override fun parseSyncAuthCode(stringCode: String): SyncAuthCode {
        // check first if it's a URL which contains the code
        val (code, wasInUrl) = kotlin.runCatching {
            SyncBarcodeUrl.parseUrl(stringCode)?.webSafeB64EncodedCode?.removeUrlSafetyToRestoreB64()
                ?.let { Pair(it, true) }
                ?: Pair(stringCode, false)
        }.getOrDefault(Pair(stringCode, false))

        if (wasInUrl && syncFeature.canScanUrlBasedSyncSetupBarcodes().isEnabled().not()) {
            logcat(ERROR) { "Feature to allow scanning URL-based sync setup codes is disabled" }
            return Unknown(code)
        }

        return kotlin.runCatching {
            val decodedCode = code.decodeB64()

            canParseAsRecoveryCode(decodedCode)?.let {
                if (wasInUrl) {
                    throw IllegalArgumentException("Sync: Recovery code found inside a URL which is not acceptable")
                } else {
                    Recovery(it)
                }
            }
                ?: canParseAsExchangeCode(decodedCode)?.let { Exchange(it) }
                ?: canParseAsConnectCode(decodedCode)?.let { Connect(it) }
                ?: Unknown(code)
        }.onSuccess {
            logcat(INFO) { "Sync: code type is ${it.javaClass.simpleName}. was inside url: $wasInUrl" }
        }.getOrElse {
            logcat(ERROR) { "Failed to decode code: ${it.asLog()}" }
            Unknown(code)
        }
    }

    private fun canParseAsRecoveryCode(decodedCode: String) = Adapters.recoveryCodeAdapter.fromJson(decodedCode)?.recovery
    private fun canParseAsExchangeCode(decodedCode: String) = Adapters.invitationCodeAdapter.fromJson(decodedCode)?.exchangeKey
    private fun canParseAsConnectCode(decodedCode: String) = Adapters.recoveryCodeAdapter.fromJson(decodedCode)?.connect

    private fun onInvitationCodeReceived(invitationCode: InvitationCode): Result<Boolean> {
        // Sync: InviteFlow - B (https://app.asana.com/0/72649045549333/1209571867429615)
        logcat { "Sync-exchange: InviteFlow - B. code is an exchange code $invitationCode" }

        // generate new ID and public/private key-pair for receiving device
        val deviceDetailsAsReceiver = kotlin.runCatching {
            generateReceiverDeviceDetails()
        }.getOrElse {
            return Error(code = EXCHANGE_FAILED.code, reason = "Error generating receiver key-pair").alsoFireAccountErrorPixel()
        }

        val invitedDeviceDetails = InvitedDeviceDetails(
            keyId = deviceDetailsAsReceiver.keyId,
            publicKey = deviceDetailsAsReceiver.publicKey,
            deviceName = syncDeviceIds.deviceName(),
        )

        val encryptedPayload = kotlin.runCatching {
            val payload = Adapters.invitedDeviceAdapter.toJson(invitedDeviceDetails)
            nativeLib.seal(payload, invitationCode.publicKey)
        }.getOrElse { throwable ->
            throwable.asErrorResult().alsoFireAccountErrorPixel()
            return Error(code = EXCHANGE_FAILED.code, reason = "Exchange: Error encrypting payload")
        }
        return syncApi.sendEncryptedMessage(invitationCode.keyId, encryptedPayload)
    }

    override fun pollForRecoveryCodeAndLogin(): Result<ExchangeResult> {
        // Sync: InviteFlow - E (https://app.asana.com/0/72649045549333/1209571867429615)
        logcat { "Sync-exchange: InviteFlow - E" }

        val pendingInvite = exchangeDeviceDetailsAsReceiver
            ?: return Error(code = EXCHANGE_FAILED.code, reason = "Exchange: No pending invite initialized").also {
                logcat(WARN) { "Sync-exchange: no pending invite initialized" }
            }

        return when (val result = syncApi.getEncryptedMessage(pendingInvite.keyId)) {
            is Error -> {
                when (result.code) {
                    NOT_FOUND.code -> {
                        logcat(VERBOSE) { "Sync-exchange: no encrypted recovery code found yet" }
                        return Success(Pending)
                    }
                    GONE.code -> {
                        logcat(WARN) { "Sync-exchange: keys expired: ${result.reason}" }
                        return Error(code = EXCHANGE_FAILED.code, reason = "Exchange: keys expired").alsoFireAccountErrorPixel()
                    }
                    else -> {
                        logcat(ERROR) { "Sync-exchange: error getting encrypted recovery code: ${result.reason}" }
                        result.alsoFireAccountErrorPixel()
                    }
                }
            }

            is Success -> {
                logcat { "Sync-exchange: received encrypted recovery code" }

                val decryptedJson = kotlin.runCatching {
                    nativeLib.sealOpen(result.data, pendingInvite.publicKey, pendingInvite.privateKey)
                }.getOrNull()
                    ?: return Error(code = EXCHANGE_FAILED.code, reason = "Connect: Error opening seal").alsoFireAccountErrorPixel()

                val recoveryData = kotlin.runCatching {
                    Adapters.recoveryCodeAdapter.fromJson(decryptedJson)?.recovery
                }.getOrNull()
                    ?: return Error(code = EXCHANGE_FAILED.code, reason = "Connect: Error reading recovery code").alsoFireAccountErrorPixel()

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

    override fun getRecoveryCode(): Result<AuthCode> {
        val primaryKey = syncStore.primaryKey ?: return Error(reason = "Get Recovery Code: Not existing primary Key").alsoFireAccountErrorPixel()
        val userID = syncStore.userId ?: return Error(reason = "Get Recovery Code: Not existing userId").alsoFireAccountErrorPixel()
        val b64Encoded = Adapters.recoveryCodeAdapter.toJson(LinkCode(RecoveryCode(primaryKey, userID))).encodeB64()

        // no additional formatting on the QR code for recovery codes, so qrCode always identical to rawCode
        return Success(AuthCode(qrCode = b64Encoded, rawCode = b64Encoded))
    }

    override fun generateExchangeInvitationCode(): Result<AuthCode> {
        // Sync: InviteFlow - A (https://app.asana.com/0/72649045549333/1209571867429615)
        logcat { "Sync-exchange: InviteFlow - A. Generating invitation code" }

        // generate new ID and and public/private key-pair for inviter device
        val deviceDetailsAsInviter = kotlin.runCatching {
            generateInviterDeviceDetails()
        }.getOrElse {
            return Error(code = EXCHANGE_FAILED.code, reason = "Error generating inviter key-pair").alsoFireAccountErrorPixel()
        }

        val invitationCode = InvitationCode(keyId = deviceDetailsAsInviter.keyId, publicKey = deviceDetailsAsInviter.publicKey)
        val invitationWrapper = InvitationCodeWrapper(invitationCode)

        return kotlin.runCatching {
            val b64Encoded = Adapters.invitationCodeAdapter.toJson(invitationWrapper).encodeB64()
            val qrCode = if (syncFeature.syncSetupBarcodeIsUrlBased().isEnabled()) {
                syncCodeUrlWrapper.wrapCodeInUrl(b64Encoded)
            } else {
                b64Encoded
            }
            Success(AuthCode(qrCode = qrCode, rawCode = b64Encoded))
        }.getOrElse {
            Error(code = EXCHANGE_FAILED.code, reason = "Error generating invitation code").alsoFireAccountErrorPixel()
        }
    }

    override fun getConnectQR(): Result<AuthCode> {
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

        val b64Encoded = linkingQRCode.encodeB64()
        val qrCode = if (syncFeature.syncSetupBarcodeIsUrlBased().isEnabled()) {
            syncCodeUrlWrapper.wrapCodeInUrl(b64Encoded)
        } else {
            b64Encoded
        }
        return Success(AuthCode(qrCode = qrCode, rawCode = b64Encoded))
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
                val recoveryCode = kotlin.runCatching {
                    Adapters.recoveryCodeAdapter.fromJson(sealOpen)?.recovery
                }.getOrNull() ?: return Error(
                    code = CONNECT_FAILED.code,
                    reason = "Connect: Error reading received recovery code",
                ).alsoFireAccountErrorPixel()
                syncStore.userId = recoveryCode.userId
                syncStore.primaryKey = recoveryCode.primaryKey
                return performLogin(recoveryCode.userId, deviceId, syncDeviceIds.deviceName(), recoveryCode.primaryKey).onFailure {
                    return it.copy(code = LOGIN_FAILED.code)
                }
            }
        }
    }

    override fun pollSecondDeviceExchangeAcknowledgement(): Result<Boolean> {
        // Sync: InviteFlow - C (https://app.asana.com/0/72649045549333/1209571867429615)
        logcat { "Sync-exchange: InviteFlow - C" }

        val keyId = exchangeDeviceDetailsAsInviter?.keyId ?: return Error(reason = "No pending invitation initialized")

        return when (val result = syncApi.getEncryptedMessage(keyId)) {
            is Error -> {
                if (result.code == NOT_FOUND.code) {
                    return Success(false)
                } else if (result.code == GONE.code) {
                    return Error(
                        code = EXCHANGE_FAILED.code,
                        reason = "Connect: keys expired",
                    ).alsoFireAccountErrorPixel()
                }
                result.alsoFireAccountErrorPixel()
            }

            is Success -> {
                logcat(VERBOSE) { "Sync-exchange: Found invitation acceptance for keyId: $keyId} ${result.data}" }

                val decrypted = kotlin.runCatching {
                    val pending = exchangeDeviceDetailsAsInviter
                        ?: return Error(code = EXCHANGE_FAILED.code, reason = "Exchange: No pending invitation initialized")
                            .alsoFireAccountErrorPixel()

                    nativeLib.sealOpen(result.data, pending.publicKey, pending.privateKey)
                }.getOrElse { throwable ->
                    throwable.asErrorResult().alsoFireAccountErrorPixel()
                    return Error(code = EXCHANGE_FAILED.code, reason = "Connect: Error opening seal")
                }

                logcat(VERBOSE) { "Sync-exchange: invitation acceptance received: $decrypted" }

                val response = Adapters.invitedDeviceAdapter.fromJson(decrypted)
                    ?: return Error(code = EXCHANGE_FAILED.code, reason = "Connect: Error reading invitation response").alsoFireAccountErrorPixel()

                val otherDevicePublicKey = response.publicKey
                val otherDeviceKeyId = response.keyId

                logcat(VERBOSE) {
                    """
                    Sync-exchange: We have received the other device's details. 
                    name:${response.deviceName}, 
                    keyId:${response.keyId}, 
                    public key: ${response.publicKey}
                    """.trimIndent()
                }

                // we encrypt our secrets with otherDevicePublicKey, and send them to the backend endpoint
                return sendSecrets(otherDeviceKeyId, otherDevicePublicKey).onFailure {
                    logcat(WARN) { "Sync-exchange: failed to send secrets. error code: ${it.code} ${it.reason}" }
                    return it.copy(code = EXCHANGE_FAILED.code)
                }
            }
        }
    }

    private fun sendSecrets(keyId: String, publicKey: String): Result<Boolean> {
        // Sync: InviteFlow - D (https://app.asana.com/0/72649045549333/1209571867429615)
        logcat { "Sync-exchange: InviteFlow - D" }

        when (val recoveryCode = getRecoveryCode()) {
            is Error -> {
                logcat(ERROR) { "Sync-exchange: failed to get recovery code. error code: ${recoveryCode.code} ${recoveryCode.reason}" }
                return Error(code = GENERIC_ERROR.code, reason = "Connect: Error getting recovery code").alsoFireAccountErrorPixel()
            }
            is Success -> {
                logcat(VERBOSE) { "Sync-exchange: Got recovery code, ready to share encrypted data for key ID: $keyId" }

                // recovery code comes b64 encoded, so we need to decode it, then encrypt, which automatically b64 encodes the encrypted form
                return kotlin.runCatching {
                    val json = recoveryCode.data.rawCode.decodeB64()
                    val encryptedJson = nativeLib.seal(json, publicKey)
                    syncApi.sendEncryptedMessage(keyId, encryptedJson)
                }.getOrElse {
                    it.asErrorResult()
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
                val codeType = parseSyncAuthCode(stringCode)
                val loginResult = processCode(codeType)
                if (loginResult is Error) {
                    syncPixels.fireUserSwitchedLoginError()
                }
                Success(true)
            }
        }
    }

    private fun generateInviterDeviceDetails(): DeviceDetailsForKeyExchange {
        logcat(INFO) { "Sync-exchange: Generating inviter device details" }
        val keyId = deviceKeyGenerator.generate()
        val prepareForConnect = nativeLib.prepareForConnect()

        return DeviceDetailsForKeyExchange(
            keyId = keyId,
            privateKey = prepareForConnect.secretKey,
            publicKey = prepareForConnect.publicKey,
        ).also {
            exchangeDeviceDetailsAsInviter = it
            logcat(WARN) { "Sync-exchange: this (inviter) device's key ID is $keyId" }
            logcat(WARN) { "Sync-exchange: this (inviter) device's public key is ${it.publicKey}" }
        }
    }

    private fun generateReceiverDeviceDetails(): DeviceDetailsForKeyExchange {
        logcat(INFO) { "Sync-exchange: Generating receiver device details" }
        val thisDeviceKeyId = deviceKeyGenerator.generate()
        val thisDeviceKeyPair = nativeLib.prepareForConnect()

        return DeviceDetailsForKeyExchange(
            keyId = thisDeviceKeyId,
            privateKey = thisDeviceKeyPair.secretKey,
            publicKey = thisDeviceKeyPair.publicKey,
        ).also {
            exchangeDeviceDetailsAsReceiver = it
            logcat(WARN) { "Sync-exchange: this (receiver) device's key ID is ${it.keyId}" }
            logcat(WARN) { "Sync-exchange: this (receiver) device's public key is ${it.publicKey}" }
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
                logcat { "Sync-Account: recovery code is ${getRecoveryCode()}" }
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
    EXCHANGE_FAILED(56),
}

sealed interface SyncAuthCode {
    data class Recovery(val b64Code: RecoveryCode) : SyncAuthCode
    data class Connect(val b64Code: ConnectCode) : SyncAuthCode
    data class Exchange(val b64Code: InvitationCode) : SyncAuthCode
    data class Unknown(val code: String) : SyncAuthCode
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

private data class DeviceDetailsForKeyExchange(
    val keyId: String,
    val privateKey: String,
    var publicKey: String,
)

/**
 * Used to indicate the result of the key exchange flow
 */
sealed interface ExchangeResult {
    /**
     * Exchange finished leaving the user logged in
     */
    data object LoggedIn : ExchangeResult

    /**
     * Exchange is currently pending, awaiting external action before it's completed
     */
    data object Pending : ExchangeResult

    /**
     * Exchange finished but the user is already logged in; account switching is required to complete the exchange and log the user in
     */
    data class AccountSwitchingRequired(val recoveryCode: String) : ExchangeResult
}

interface DeviceKeyGenerator {
    fun generate(): String
}

@ContributesBinding(AppScope::class)
class RealDeviceKeyGenerator @Inject constructor() : DeviceKeyGenerator {
    override fun generate(): String {
        return UUID.randomUUID().toString()
    }
}
