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

import android.util.Base64
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
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.impl.metrics.ConnectedDevicesObserver
import com.duckduckgo.sync.impl.pixels.*
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrlWrapper
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import com.duckduckgo.sync.store.*
import com.squareup.anvil.annotations.*
import com.squareup.moshi.*
import dagger.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import java.util.UUID
import javax.inject.*

interface SyncAccountRepository {

    fun parseSyncAuthCode(stringCode: String): SyncAuthCode
    fun isSyncSupported(): Boolean

    @WorkerThread
    fun createAccount(): Result<Boolean>
    fun isSignedIn(): Boolean
    fun processCode(code: SyncAuthCode, existingDeviceId: String? = null): Result<Boolean>
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

    /**
     * Ensures the `3party` credential exists for this account, so [getThirdPartyRecoveryCode]
     * can later produce a code shareable with a 3rd-party browser.
     *
     * If another device on the account has already created it, the existing credential is
     * adopted locally; otherwise a new one is created on the server. Either path leaves the
     * local store ready for [getThirdPartyRecoveryCode].
     */
    fun createThirdPartyCredential(): Result<Boolean>

    /**
     * Fetches the 3party credential from the server, decrypts the SP using the account's secretKey,
     * and stores it locally in [SyncStore.scopedPassword]. Used to recover the SP on a device that
     * didn't create the credential (e.g. a different device on the same account).
     *
     * Returns [Result.Success] with `true` if the credential was fetched and stored, or `false` if
     * no 3party credential exists on the server. Returns [Result.Error] only for actual failures
     * (network errors, decrypt failures, etc.).
     */
    fun refreshThirdPartyCredential(): Result<Boolean>

    /**
     * Joins this device to an existing account using a 3party recovery code, executing the
     * "Native joining a 3party account" upgrade flow per the Unified Algorithm (Asana
     * 1214739740392701). Two network calls:
     *
     *   1. POST /sync/login with scope=ai_chats — authenticates against the existing 3party
     *      credential and returns a short-lived token + the protected keys to re-wrap.
     *   2. POST /access-credentials/ddg — mints a fresh DDG credential on the account, attached
     *      alongside the existing 3party entry (which gets decorated with encrypted_3party_credential
     *      so future ddg-side logins can re-derive SP).
     *
     * On success the device ends in a normal Native signed-in state: full primaryKey / secretKey
     * populated, credentialId=ddg, scopedPassword populated with SP. SyncStore is written
     * atomically only after both network calls succeed; observers never see an intermediate state.
     *
     * Intentionally NOT routed through [processCode]. Production paste/scan paths must not accept
     * 3party codes directly per user constraint. Called only from the dev-tool entrypoint and
     * (future) the v2 exchange final-step handler.
     */
    fun joinAccountFromThirdPartyRecoveryCode(pastedCode: String): Result<Boolean>

    /**
     * Returns a recovery code that a 3rd-party browser can use to sign in and access this
     * account's scoped data. Requires the 3party credential to already exist locally — call
     * [createThirdPartyCredential] (or [refreshThirdPartyCredential]) first.
     */
    fun getThirdPartyRecoveryCode(): Result<AuthCode>

    /**
     * Creates a protected RSA keypair for the given purpose (e.g. "ai_chats") and uploads it
     * to the server, encrypted with the ddg credential's stretchedPrimaryKey.
     *
     * No-op (returns existing key) if a key for the purpose already exists.
     */
    fun createProtectedKey(purpose: String): Result<Boolean>

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
    private val connectedDevicesObserver: ConnectedDevicesObserver,
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
    private val syncSetupWideEvent: SyncSetupWideEvent,
    private val syncJweCrypto: SyncJweCrypto,
    private val thirdPartyCredentialManager: ThirdPartyCredentialManager,
    private val protectedKeyManager: ProtectedKeyManager,
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

    override fun processCode(code: SyncAuthCode, existingDeviceId: String?): Result<Boolean> {
        when (code) {
            is Recovery -> {
                logcat { "Sync: code is a recovery code" }
                return login(code.b64Code, existingDeviceId)
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

    // `primaryKey` and `userId` are declared non-null but reflection-based Moshi can populate them with null when the JSON keys are missing
    @Suppress("SENSELESS_COMPARISON")
    private fun canParseAsRecoveryCode(decodedCode: String) = Adapters.recoveryCodeAdapter.fromJson(decodedCode)?.recovery
        ?.takeIf { it.primaryKey != null && it.userId != null }
    private fun canParseAsExchangeCode(decodedCode: String) = Adapters.invitationCodeAdapter.fromJson(decodedCode)?.exchangeKey
    private fun canParseAsConnectCode(decodedCode: String) = Adapters.recoveryCodeAdapter.fromJson(decodedCode)?.connect

    /**
     * Parse a base64url-encoded 3party recovery code (v2 format per the Recovery Payload Shape
     * RFC, Asana 1214804486778180). Returns the inner payload if it's a structurally-valid 3party
     * code, null otherwise. Errors (bad base64, bad JSON, missing fields) are swallowed and turned
     * into null — callers translate null into a user-facing error.
     *
     * Discrimination: `recovery.cid == "3party"` AND `recovery.v` starts with "2." (clients in
     * major version 2 accept any 2.x code) AND `secret`/`user_id` are non-empty.
     *
     * Intentionally NOT wired into [parseSyncAuthCode]. Production paste/scan paths route through
     * [canParseAsRecoveryCode], which rejects 3party codes via an explicit non-null check on
     * `primary_key` / `user_id` (the [Adapters] Moshi builder doesn't use [KotlinJsonAdapterFactory],
     * so reflection-based parsing happily populates non-null Kotlin properties with `null` — the
     * explicit check is what enforces v1 shape). Only the dev-tool entrypoint and the v2 exchange
     * final-step handler invoke this helper directly.
     */
    private fun parseThirdPartyRecoveryCode(pastedCode: String): ThirdPartyRecoveryCode? {
        return kotlin.runCatching {
            val decodedBytes = Base64.decode(pastedCode, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val decodedJson = decodedBytes.toString(Charsets.UTF_8)
            val parsed = Adapters.thirdPartyRecoveryCodeAdapter.fromJson(decodedJson)?.recovery ?: return@runCatching null
            if (parsed.cid != CREDENTIAL_ID_3PARTY) return@runCatching null
            if (!parsed.v.startsWith("2.")) return@runCatching null
            if (parsed.secret.isEmpty() || parsed.userId.isEmpty()) return@runCatching null
            parsed
        }.getOrNull()
    }

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

    private fun login(recoveryCode: RecoveryCode, existingDeviceId: String? = null): Result<Boolean> {
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
        val deviceId = existingDeviceId ?: syncDeviceIds.deviceId()
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

    override fun getThirdPartyRecoveryCode(): Result<AuthCode> {
        return when (val result = thirdPartyCredentialManager.getRecoveryCode()) {
            is Success -> Success(AuthCode(qrCode = result.data, rawCode = result.data))
            is Error -> result
        }
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

    override fun createThirdPartyCredential(): Result<Boolean> = thirdPartyCredentialManager.create()

    override fun refreshThirdPartyCredential(): Result<Boolean> = thirdPartyCredentialManager.refresh()

    override fun createProtectedKey(purpose: String): Result<Boolean> = protectedKeyManager.create(purpose)

    /**
     * Bundle produced by [buildThirdPartyUpgradePackage] and consumed by the upgrade POST +
     * SyncStore commit step. Keeps the locally-generated DDG account material together with the
     * request body and the re-wrapped keys list, so the caller can perform a single atomic write.
     */
    private data class ThirdPartyUpgradePackage(
        val newDdgKeys: AccountKeys,
        val rewrappedKeys: List<ProtectedKeyEntry>,
        val request: CreateAccessCredentialRequest,
    )

    /**
     * Step 3 + 4 + 5 of the "Native joining a 3party account" upgrade flow. Given a parsed
     * 3party recovery code and the protected keys returned by the prior /sync/login (encrypted
     * by FE with the 3party MEK in JWE format), build everything needed for POST
     * /access-credentials/ddg:
     *
     *   - generate a fresh DDG credential locally (new MP/SK/PSK/PH via libsodium)
     *   - re-wrap each FE-written key with the new DDG secretKey using libsodium (Native pattern,
     *     encrypted_with="ddg"). Source format is RFC 7516 JWE compact; target is libsodium-secretbox
     *     base64url-encoded — same asymmetry as the reverse direction in [createThirdPartyCredential].
     *   - encrypt SP with the new DDG MEK in a JWE-dir-A256GCM envelope with kid="ddg" (the
     *     `encrypted_3party_credential` field that decorates the *existing* 3party entry so a
     *     future ddg-side login can derive SP back from MP).
     *
     * Reference: Sync API docs POST /access-credentials/{id} ("upgrades a 3party-only account into
     * a native one") and Encryption Algorithms TD (Asana 1214802412121967).
     *
     * No network calls and no SyncStore writes — both are the responsibility of the caller.
     */
    private fun buildThirdPartyUpgradePackage(
        parsed: ThirdPartyRecoveryCode,
        keysFromLogin: List<ProtectedKeyEntry>,
    ): Result<ThirdPartyUpgradePackage> {
        // SP travels in the recovery code as base64url; nativeLib + hkdfDerive* helpers expect
        // standard base64. Compute once at the boundary, reuse for all derivations.
        val spStandardB64 = kotlin.runCatching { base64UrlStringToStandardBase64(parsed.secret) }
            .getOrElse { return Error(reason = "Upgrade: failed to decode SP: ${it.message}") }
        val hkdfSalt = parsed.userId.toByteArray(Charsets.UTF_8)

        // SP-derived MEK: used to decrypt FE-written (JWE) keys we received from /sync/login.
        val spMek = kotlin.runCatching { syncJweCrypto.hkdfDeriveBytes(spStandardB64, hkdfSalt, "Main Key", 32) }
            .getOrElse { return Error(reason = "Upgrade: failed to derive SP MEK: ${it.message}") }

        // Fresh DDG credential generated locally. nativeLib.generateAccountKeys returns a tuple
        // where `primaryKey` is the new account's MP (the seed for all spec-side derivations) and
        // `secretKey` is the libsodium secretbox key used to wrap encrypted_private_key entries.
        val newDdgKeys = kotlin.runCatching {
            nativeLib.generateAccountKeys(userId = parsed.userId).also {
                it.checkResult("Upgrade: DDG account key generation failed")
            }
        }.getOrElse { return it.asErrorResult() }

        // DDG-derived MEK: used to encrypt `encrypted_3party_credential` (the SP-decorates-3party
        // payload). Per Encryption Algorithms TD §"How do we encrypt the 3party secret using the
        // DDG's MEK?", this is HKDF(MP, salt=user_id, info="Main Key", 32 bytes) imported as
        // AES-GCM-256. Pinned against the TD's test3 vector in SyncJweCryptoTdVectorsTest.
        val ddgMek = kotlin.runCatching { syncJweCrypto.hkdfDeriveBytes(newDdgKeys.primaryKey, hkdfSalt, "Main Key", 32) }
            .getOrElse { return Error(reason = "Upgrade: failed to derive DDG MEK: ${it.message}") }

        // encrypted_3party_credential: JWE compact (alg=dir, enc=A256GCM, kid="ddg") of the SP
        // base64url STRING (matches the reverse direction in createThirdPartyCredential, which
        // encrypts the base64url SP string — not the raw SP bytes — so the wire is symmetrical).
        val encryptedThreePartyCredential = kotlin.runCatching {
            syncJweCrypto.jweEncryptSymmetric(parsed.secret.toByteArray(Charsets.UTF_8), ddgMek, kid = CREDENTIAL_ID_DDG)
        }.getOrElse { return Error(reason = "Upgrade: failed to encrypt encrypted_3party_credential: ${it.message}") }

        // Re-wrap each FE-written key from /sync/login. Decrypt via JWE using SP MEK, then
        // re-encrypt with libsodium-secretbox using the new DDG secretKey, matching the Native
        // wire format (base64-encoded encrypted bytes with URL safety applied — mirrors
        // createProtectedKey at line ~955 and the reverse direction at line ~770).
        val rewrappedKeys = keysFromLogin.map { srcKey ->
            kotlin.runCatching {
                // FE-only accounts always write keys with encrypted_with="3party". A defensive
                // skip-or-fail decision: bail if we see anything else, since re-wrapping a key we
                // can't decrypt would silently break ai_chats sync after upgrade.
                if (srcKey.encryptedWith != CREDENTIAL_ID_3PARTY) {
                    error("Upgrade: cannot re-wrap key kid=${srcKey.kid} encrypted_with=${srcKey.encryptedWith}; expected 3party")
                }
                val rawPrivateKeyBytes = syncJweCrypto.jweDecryptSymmetric(srcKey.encryptedPrivateKey, spMek)
                val encryptedResult = nativeLib.encryptData(rawPrivateKeyBytes, newDdgKeys.secretKey).also {
                    it.checkResult("Upgrade: libsodium encryption of re-wrapped key kid=${srcKey.kid} failed")
                }
                val wireEncryptedPrivateKey =
                    Base64.encodeToString(encryptedResult.encryptedData, Base64.NO_WRAP).applyUrlSafetyFromB64()
                ProtectedKeyEntry(
                    kid = srcKey.kid,
                    purpose = srcKey.purpose,
                    encryptedWith = CREDENTIAL_ID_DDG,
                    encryptedPrivateKey = wireEncryptedPrivateKey,
                    publicKey = srcKey.publicKey,
                )
            }
        }
        val firstFailure = rewrappedKeys.firstOrNull { it.isFailure }
        if (firstFailure != null) {
            val cause = firstFailure.exceptionOrNull()
            logcat(ERROR) { "Sync-ScopedToken: failed to re-wrap one or more protected keys for upgrade: ${cause?.message}" }
            return Error(reason = "Upgrade: re-wrap key failed: ${cause?.message}")
        }
        val rewrappedKeysList = rewrappedKeys.map { it.getOrThrow() }

        // hashedPassword (re-auth against the existing 3party credential) — same HKDF derivation
        // we used at /sync/login. Per Sync API docs, the POST /access-credentials/{id} body carries
        // hashed_password as a re-auth of ANY existing credential, allowing the server to decrypt
        // the e2ee_id and accept the new credential atomically.
        val hashedPasswordForReauth = kotlin.runCatching { syncJweCrypto.hkdfDeriveBase64Url(spStandardB64, hkdfSalt, "Password", 32) }
            .getOrElse { return Error(reason = "Upgrade: failed to derive 3party hashed_password: ${it.message}") }

        // credentialHashedPassword for the new DDG credential — HKDF(MP, salt=user_id, info="Password", 32).
        // The server stores twice_hash(this) and validates future /login submissions against it.
        // Pinned against the TD's test1 vector in SyncJweCryptoTdVectorsTest.
        val credentialHashedPassword = kotlin.runCatching {
            syncJweCrypto.hkdfDeriveBase64Url(newDdgKeys.primaryKey, hkdfSalt, "Password", 32)
        }.getOrElse { return Error(reason = "Upgrade: failed to derive new DDG credential_hashed_password: ${it.message}") }

        val request = CreateAccessCredentialRequest(
            hashedPassword = hashedPasswordForReauth,
            credentialHashedPassword = credentialHashedPassword,
            protectedEncryptionKey = newDdgKeys.protectedSecretKey,
            encrypted3partyCredential = encryptedThreePartyCredential,
            keys = rewrappedKeysList.ifEmpty { null },
        )

        return Success(
            ThirdPartyUpgradePackage(
                newDdgKeys = newDdgKeys,
                rewrappedKeys = rewrappedKeysList,
                request = request,
            ),
        )
    }

    override fun joinAccountFromThirdPartyRecoveryCode(pastedCode: String): Result<Boolean> {
        if (!syncFeature.canUseV2ConnectFlow().isEnabled()) {
            return Error(reason = "JoinFrom3party: canUseV2ConnectFlow is disabled")
        }

        logcat { "Sync-ScopedToken: joining account via 3party recovery code" }

        val parsed = parseThirdPartyRecoveryCode(pastedCode)
            ?: return Error(reason = "JoinFrom3party: code is not a valid 3party recovery code")

        val deviceId = syncDeviceIds.deviceId()
        val deviceName = syncDeviceIds.deviceName()

        // Step 2 — authenticate against the 3party credential.
        val loginResponse = when (val loginResult = performThirdPartyLogin(parsed, deviceId)) {
            is Error -> return loginResult.copy(reason = "JoinFrom3party: ${loginResult.reason}")
            is Success -> loginResult.data
        }

        // Step 2a — "Does it need an upgrade?" check per Unified Algorithm (Asana 1214739740392701,
        // "Native only - Upgrading 3party account"). The /sync/login response includes the account's
        // current access_credentials, so we can detect a pre-existing ddg credential without a
        // separate GET. If ddg already exists, abort with the spec-defined error rather than letting
        // the flow fail downstream with a misleading message.
        val accountAlreadyHasDdg = loginResponse.accessCredentials.orEmpty().any { it.id == CREDENTIAL_ID_DDG }
        if (accountAlreadyHasDdg) {
            logcat(WARN) { "Sync-ScopedToken: account already has a ddg credential — cannot re-upgrade from 3party" }
            return Error(
                reason = "JoinFrom3party: account already upgraded. " +
                    "Please use one of the already connected Native DDG Applications to add another one.",
            )
        }

        // Only 3party-encrypted keys are re-wrappable from this login. Defensive filter: the BE
        // returns every key the account has, including any encrypted_with=ddg entries from a prior
        // upgrade attempt. Without the Step 2a check above those would be caught here too, but
        // filtering also future-proofs against unknown credential types appearing in the response.
        val keysFromLogin = loginResponse.keys.orEmpty().filter { it.encryptedWith == CREDENTIAL_ID_3PARTY }
        logcat { "Sync-ScopedToken: 3party /sync/login OK, ${keysFromLogin.size} key(s) to re-wrap" }

        // Steps 3 + 4 + 5 — build the upgrade payload locally. Pure compute, no network, no SyncStore.
        val upgradePackage = when (val pkg = buildThirdPartyUpgradePackage(parsed, keysFromLogin)) {
            is Error -> return pkg.copy(reason = "JoinFrom3party: ${pkg.reason}")
            is Success -> pkg.data
        }

        // Step 6 — POST /access-credentials/ddg. If interrupted mid-flight, the BE's 5-minute TTL
        // on newly-minted credentials cleans up the orphan automatically (Unified Algorithm,
        // Backend-supported Alternative). No client-side reconcile needed.
        val postResult = syncApi.createAccessCredential(loginResponse.token, CREDENTIAL_ID_DDG, upgradePackage.request)
        if (postResult is Error) {
            if (postResult.code == API_CODE.COUNT_LIMIT.code) {
                // 409 credential_already_exists: either another device beat us to creating a ddg
                // credential, OR a previous attempt by this device crashed mid-flight and the BE
                // will auto-remove the orphan after its 5-minute TTL. The two are indistinguishable
                // from the response, so we surface the spec-defined error (Unified Algorithm,
                // Asana 1214739740392701) — in the crash-recovery case the user can retry
                // successfully after 5 minutes, per the spec author's accepted UX trade-off.
                logcat(WARN) { "Sync-ScopedToken: 409 — account already has a ddg credential" }
                return Error(
                    code = postResult.code,
                    reason = "JoinFrom3party: account already has a ddg credential. " +
                        "Please use one of the already connected Native DDG Applications to add another one.",
                )
            }
            logcat(ERROR) { "Sync-ScopedToken: /access-credentials/ddg POST failed: ${postResult.reason}" }
            return postResult.copy(reason = "JoinFrom3party: ${postResult.reason}")
        }

        // Step 7a — Flow 4 native login with the new ddg credential. This is the BE-side commit
        // for the credential just POSTed: without a login inside the 5-minute TTL the server
        // auto-removes the credential. It also yields the unrestricted ddg-scoped token that
        // device-management endpoints need (the 3party login token at this point is ai_chats-only).
        val ddgLoginResponse = when (
            val ddgLogin = performDdgLoginForUpgrade(
                userId = parsed.userId,
                deviceId = deviceId,
                deviceName = deviceName,
                primaryKey = upgradePackage.newDdgKeys.primaryKey,
            )
        ) {
            is Error -> {
                if (ddgLogin.code == API_CODE.INVALID_LOGIN_CREDENTIALS.code) {
                    logcat(ERROR) {
                        "Sync-ScopedToken: ddg login after upgrade returned 401 — credential 5-minute TTL likely expired"
                    }
                } else {
                    logcat(ERROR) { "Sync-ScopedToken: ddg login after upgrade failed: ${ddgLogin.reason}" }
                }
                return ddgLogin.copy(reason = "JoinFrom3party: post-upgrade ddg login failed: ${ddgLogin.reason}")
            }
            is Success -> ddgLogin.data
        }

        // Step 7b — atomic SyncStore commit. Everything above has either failed (returning Error
        // without mutating SyncStore) or succeeded. External observers see the device as
        // pre-upgrade until this block runs.
        val spStandardB64 = base64UrlStringToStandardBase64(parsed.secret)
        syncStore.storeCredentials(
            userId = parsed.userId,
            deviceId = deviceId,
            deviceName = deviceName,
            primaryKey = upgradePackage.newDdgKeys.primaryKey,
            secretKey = upgradePackage.newDdgKeys.secretKey,
            token = ddgLoginResponse.token,
        )
        syncStore.credentialId = CREDENTIAL_ID_DDG
        syncStore.scopedPassword = ScopedPassword(spStandardB64)
        syncStore.protectedKeysJson = Adapters.protectedKeysAdapter.toJson(upgradePackage.rewrappedKeys)

        logcat { "Sync-ScopedToken: 3party→ddg upgrade complete; account joined as ddg" }
        return Success(true)
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
        syncPixels.fireUserConfirmedToTurnOffSyncAndDelete(connectedDevicesCached.size)

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
                    }.also { devices ->
                        connectedDevicesCached.apply {
                            clear()
                            addAll(devices)
                        }
                        connectedDevicesObserver.onDevicesUpdated(devices)
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

        val credentialId = if (syncFeature.canUseV2ConnectFlow().isEnabled()) CREDENTIAL_ID_DDG else null

        val result = try {
            runBlocking { syncSetupWideEvent.onAccountCreationApiStarted() }
            syncApi.createAccount(
                account.userId,
                account.passwordHash,
                account.protectedSecretKey,
                deviceId,
                encryptedDeviceName,
                encryptedDeviceType,
                credentialId,
            )
        } finally {
            runBlocking { syncSetupWideEvent.onAccountCreationApiFinished() }
        }

        return when (result) {
            is Error -> {
                result
            }

            is Success -> {
                syncStore.storeCredentials(account.userId, deviceId, deviceName, account.primaryKey, account.secretKey, result.data.token)
                if (syncFeature.canUseV2ConnectFlow().isEnabled()) {
                    logcat { "Sync-ScopedToken: setting credentialId=ddg after account creation" }
                    syncStore.credentialId = CREDENTIAL_ID_DDG
                }
                try {
                    runBlocking { syncSetupWideEvent.onInitialSyncStarted() }
                    syncEngine.triggerSync(ACCOUNT_CREATION)
                } finally {
                    runBlocking { syncSetupWideEvent.onInitialSyncFinished() }
                }
                logcat { "Sync-Account: recovery code is ${getRecoveryCode()}" }
                Success(true)
            }
        }
    }

    /**
     * Step 2 of the "Native joining a 3party account" upgrade flow (Unified Algorithm, Asana
     * 1214739740392701). Authenticates against the server using a parsed 3party recovery code
     * and returns the LoginResponse so the caller can use the token + keys[] to perform the
     * subsequent POST /access-credentials/ddg upgrade.
     *
     * The returned token is short-lived and ONLY used to mint the new ddg credential — it is
     * never persisted to SyncStore (atomic commit happens after the full upgrade succeeds).
     *
     * Wire body per Sync API docs (POST /sync/login):
     *   hashed_password = HKDF-SHA-256(SP, salt=user_id_utf8, info="Password", 32) → base64url
     *                     (Encryption Algorithms TD, Asana 1214802412121967, §"Hashed password derivation")
     *   scope           = "ai_chats" — the canonical scope for 3party-restricted credentials
     *   device_name,
     *   device_type     = libsodium-encrypted with SP-as-key (mirrors the existing ddg pattern;
     *                     server treats these as opaque blobs)
     *
     * Response carries token + keys[] for downstream re-wrap. NO protected_encryption_key —
     * 3party credentials don't carry one (Sync API docs, POST /sync/login Notes).
     */
    private fun performThirdPartyLogin(
        parsed: ThirdPartyRecoveryCode,
        deviceId: String,
    ): Result<LoginResponse> {
        // SP travels in the recovery code as base64url; nativeLib + hkdfDerive* helpers expect
        // standard base64 input. Convert at the boundary.
        val spStandardB64 = kotlin.runCatching { base64UrlStringToStandardBase64(parsed.secret) }
            .getOrElse { return Error(reason = "3party login: failed to decode SP: ${it.message}") }

        val hkdfSalt = parsed.userId.toByteArray(Charsets.UTF_8)
        val hashedPassword = kotlin.runCatching { syncJweCrypto.hkdfDeriveBase64Url(spStandardB64, hkdfSalt, "Password", 32) }
            .getOrElse { return Error(reason = "3party login: failed to derive hashed_password: ${it.message}") }

        val deviceName = syncDeviceIds.deviceName()
        val deviceType = syncDeviceIds.deviceType()
        val encryptedDeviceName = kotlin.runCatching {
            nativeLib.encryptData(deviceName, spStandardB64).also {
                it.checkResult("3party login: encrypting device name failed")
            }.encryptedData
        }.getOrElse { return it.asErrorResult() }
        val encryptedDeviceType = kotlin.runCatching {
            nativeLib.encryptData(deviceType.deviceFactor, spStandardB64).also {
                it.checkResult("3party login: encrypting device type failed")
            }.encryptedData
        }.getOrElse { return it.asErrorResult() }

        return syncApi.login(
            userID = parsed.userId,
            hashedPassword = hashedPassword,
            deviceId = deviceId,
            deviceName = encryptedDeviceName,
            deviceType = encryptedDeviceType,
            scope = SYNC_SCOPE_AI_CHATS,
        )
    }

    /**
     * Flow 4 native login (unrestricted scope) issued immediately after a successful
     * POST /access-credentials/ddg during the 3party→ddg upgrade. Per the Unified Algorithm
     * (Asana 1214739740392701, "Native only - Upgrading 3party account, Backend-supported
     * Alternative"), this login acts as the BE-side *commit* for the newly minted credential —
     * without it, the server removes the credential after a 5-minute TTL. It is also what
     * yields an unrestricted ddg-scoped token; the 3party token from [performThirdPartyLogin]
     * is `scope=ai_chats` and cannot drive device-management endpoints.
     *
     * Returns the [LoginResponse] for the caller to commit alongside the new local key
     * material. Does not write to [syncStore].
     */
    private fun performDdgLoginForUpgrade(
        userId: String,
        deviceId: String,
        deviceName: String,
        primaryKey: String,
    ): Result<LoginResponse> {
        // HKDF-derived hashed_password matching the `credentialHashedPassword` we POSTed in
        // [buildThirdPartyUpgradePackage] — the upgrade-created ddg credential was registered with
        // the v2 cross-platform algorithm (Encryption Algorithms TD: HKDF(MP, salt=user_id,
        // info="Password", 32)), NOT v1's libsodium BLAKE2b. Using nativeLib.prepareForLogin(...)
        // here would 401 because its passwordHash uses BLAKE2b.
        val hkdfSalt = userId.toByteArray(Charsets.UTF_8)
        val hashedPassword = kotlin.runCatching {
            syncJweCrypto.hkdfDeriveBase64Url(primaryKey, hkdfSalt, "Password", 32)
        }.getOrElse { return Error(reason = "Upgrade ddg login: derive hashed_password failed: ${it.message}") }

        // Device-field encryption uses the raw primaryKey directly — same pattern as
        // [getConnectedDevices] (decrypt) and [signup] (encrypt), so the device list round-trips
        // cleanly. [performLogin] also uses the raw key (LoginKeys.primaryKey is the pass-through
        // raw value, not the stretched one).
        val deviceType = syncDeviceIds.deviceType()
        val encryptedDeviceName = kotlin.runCatching {
            nativeLib.encryptData(deviceName, primaryKey).also {
                it.checkResult("Upgrade ddg login: encrypt device name failed")
            }.encryptedData
        }.getOrElse { return it.asErrorResult() }
        val encryptedDeviceType = kotlin.runCatching {
            nativeLib.encryptData(deviceType.deviceFactor, primaryKey).also {
                it.checkResult("Upgrade ddg login: encrypt device type failed")
            }.encryptedData
        }.getOrElse { return it.asErrorResult() }

        return syncApi.login(
            userID = userId,
            hashedPassword = hashedPassword,
            deviceId = deviceId,
            deviceName = encryptedDeviceName,
            deviceType = encryptedDeviceType,
            scope = null,
        )
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
                // PEK is required for ddg logins (it carries the protected secretKey). Absence here
                // would indicate either a server contract change or a 3party credential being
                // resolved against this code path — neither is recoverable.
                val protectedEncryptionKey = result.data.protected_encryption_key
                    ?: return Error(reason = "Login: server returned no protected_encryption_key for ddg credential")
                val decryptResult = kotlin.runCatching {
                    nativeLib.decrypt(protectedEncryptionKey, preLogin.stretchedPrimaryKey).also {
                        it.checkResult("Login: decrypt protection keys failed")
                    }
                }.getOrElse { throwable ->
                    return throwable.asErrorResult()
                }

                syncStore.storeCredentials(userId, deviceId, deviceName, preLogin.primaryKey, decryptResult.decryptedData, result.data.token)

                if (syncFeature.canUseV2ConnectFlow().isEnabled()) {
                    logcat { "Sync-ScopedToken: login response has v2 data, storing" }
                    syncStore.credentialId = CREDENTIAL_ID_DDG
                    result.data.accessCredentials?.let { credentials ->
                        logcat { "Sync-ScopedToken: ${credentials.size} access credential(s) in response" }
                        val thirdParty = credentials.find { it.id == CREDENTIAL_ID_3PARTY }
                        val encryptedSp = thirdParty?.encryptedCredential
                        if (encryptedSp != null) {
                            // Decrypt with the DDG MEK; convert wire base64url to standard base64 for storage.
                            val decryptedSp = kotlin.runCatching {
                                val ddgMek = syncJweCrypto.hkdfDeriveBytes(preLogin.primaryKey, userId.toByteArray(Charsets.UTF_8), "Main Key", 32)
                                val b64Url = String(syncJweCrypto.jweDecryptSymmetric(encryptedSp, ddgMek), Charsets.UTF_8)
                                base64UrlStringToStandardBase64(b64Url)
                            }.getOrElse {
                                logcat(ERROR) { "Sync-ScopedToken: failed to decrypt SP from server: ${it.message}" }
                                null
                            }
                            if (decryptedSp != null) {
                                syncStore.scopedPassword = ScopedPassword(decryptedSp)
                            }
                        }
                    }
                    result.data.keys?.let { keys ->
                        logcat { "Sync-ScopedToken: ${keys.size} protected key(s) in response" }
                        val ddgKeys = keys.filter { it.encryptedWith == CREDENTIAL_ID_DDG }
                        val keysJson = Adapters.protectedKeysAdapter.toJson(ddgKeys)
                        syncStore.protectedKeysJson = keysJson
                    }
                }

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
            val thirdPartyRecoveryCodeAdapter: JsonAdapter<ThirdPartyRecoveryCodeWrapper> = moshi.adapter(ThirdPartyRecoveryCodeWrapper::class.java)

            val invitationCodeAdapter: JsonAdapter<InvitationCodeWrapper> = moshi.adapter(InvitationCodeWrapper::class.java)
            val invitedDeviceAdapter: JsonAdapter<InvitedDeviceDetails> = moshi.adapter(InvitedDeviceDetails::class.java)
            val protectedKeysAdapter: JsonAdapter<List<ProtectedKeyEntry>> =
                moshi.adapter(Types.newParameterizedType(List::class.java, ProtectedKeyEntry::class.java))
        }
    }
}

// Credential IDs known to the Sync API. Used in URL paths (e.g. POST /access-credentials/3party),
// as the local credentialId marker in SyncStore, and as the credential_id field in 3party
// recovery codes.
internal const val CREDENTIAL_ID_DDG = "ddg"
internal const val CREDENTIAL_ID_3PARTY = "3party"

// Scope values for the /sync/login `scope` parameter. Absent / null means unrestricted (defaults
// to "sync"). Per Sync API docs (POST /sync/login Notes), "ai_chats" is the canonical scope used
// when authenticating against a 3party-restricted credential.
internal const val SYNC_SCOPE_AI_CHATS = "ai_chats"

// Recovery code version emitted in v2 recovery codes per the Recovery Payload Shape RFC
// (Asana 1214804486778180). Format is "major.minor" — clients in major version 2 accept 2.x
// codes (ignoring unknown fields) and must reject codes with major version >2.
internal const val RECOVERY_CODE_V2 = "2.0"

internal fun SyncCryptoResult.checkResult(errorMessage: String) {
    if (result != 0) {
        throw SyncAccountException(this.result, errorMessage)
    }
}

internal fun Throwable.asErrorResult(): Error {
    return when (this) {
        is SyncAccountException -> Error(code = this.code, reason = this.message.toString())
        else -> Error(reason = this.message.toString())
    }
}

internal class SyncAccountException(
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

/**
 * 3party recovery code envelope. Shares the `recovery` top-level key with [LinkCode];
 * versions are disambiguated by the inner `v` field per Asana 1214804486778180.
 */
data class ThirdPartyRecoveryCodeWrapper(
    val recovery: ThirdPartyRecoveryCode,
)

/**
 * 3party recovery code v2 payload per Asana 1214804486778180. Outer JSON is base64url-encoded
 * for transport; `secret` is base64url of the 32 raw SP bytes.
 */
data class ThirdPartyRecoveryCode(
    @field:Json(name = "user_id") val userId: String,
    val secret: String,
    val cid: String,
    val v: String,
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
