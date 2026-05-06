/*
 * Copyright (c) 2026 DuckDuckGo
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
import androidx.annotation.WorkerThread
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.AccountErrorCodes.GENERIC_ERROR
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.store.ScopedPassword
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

/**
 * Lifecycle owner for the 3party access credential — the scoped credential shared between native
 * sync and 3rd-party browser surfaces. Hosts the create/refresh/recovery-code paths so they can be
 * tested in isolation from the larger account repository.
 */
interface ThirdPartyCredentialManager {

    /**
     * Ensures the `3party` credential exists for this account. If another device on the account
     * already created it, the existing credential is adopted locally; otherwise a new one is created
     * on the server. Either path leaves [SyncStore.scopedPassword] populated.
     */
    fun create(): Result<Boolean>

    /**
     * Fetches the 3party credential from the server, decrypts its SP using the account's primary
     * key, and stores the result locally. Used to recover the SP on a device that didn't create the
     * credential. Returns [Success] with `true` if stored, `false` if no credential exists yet, and
     * [Error] for genuine failures.
     */
    fun refresh(): Result<Boolean>

    /**
     * Emits a v2 3party recovery code (base64url-encoded JSON) suitable for sharing with a
     * 3rd-party browser. Requires a local SP — call [create] first.
     */
    fun getRecoveryCode(): Result<String>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
@WorkerThread
class RealThirdPartyCredentialManager @Inject constructor(
    private val syncStore: SyncStore,
    private val syncApi: SyncApi,
    private val syncJweCrypto: SyncJweCrypto,
    private val nativeLib: SyncLib,
    private val syncFeature: SyncFeature,
) : ThirdPartyCredentialManager {

    override fun create(): Result<Boolean> {
        val inputs = when (val r = validateCreatePreconditions()) {
            is Success -> r.data
            is Error -> return r
        }
        logcat { "Sync-ScopedToken: creating 3party credential" }

        return when (val adopted = tryAdoptExistingCredential(inputs)) {
            AdoptResult.Adopted -> Success(true)
            is AdoptResult.Failed -> adopted.error
            AdoptResult.NotFound -> mintAndPostNewCredential(inputs)
        }
    }

    /** Validated inputs needed by every phase of the create flow. Not a data class — the
     *  ByteArray salt would have surprising reference-based equality. */
    private class CreateInputs(
        val token: String,
        val primaryKey: String,
        val userId: String,
        val hkdfSalt: ByteArray,
    )

    private sealed class AdoptResult {
        object Adopted : AdoptResult()
        object NotFound : AdoptResult()
        data class Failed(val error: Error) : AdoptResult()
    }

    private fun validateCreatePreconditions(): Result<CreateInputs> {
        if (!syncFeature.canUseV2ConnectFlow().isEnabled()) {
            return Error(reason = "Scoped access credentials feature is disabled")
        }
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: return Error(reason = "CreateThirdPartyCredential: not signed in")
        val primaryKey = syncStore.primaryKey
            ?: return Error(reason = "CreateThirdPartyCredential: no primary key")
        val userId = syncStore.userId
            ?: return Error(reason = "CreateThirdPartyCredential: no userId")
        // HKDF salt = user_id UTF-8 bytes, per Encryption Algorithms TD (Asana 1214802412121967).
        return Success(CreateInputs(token, primaryKey, userId, userId.toByteArray(Charsets.UTF_8)))
    }

    /** If another device already created the 3party credential, decrypt its SP and store locally. */
    private fun tryAdoptExistingCredential(inputs: CreateInputs): AdoptResult {
        val existing = when (val r = syncApi.getAccessCredentials(inputs.token)) {
            is Success -> r.data.find { it.id == CREDENTIAL_ID_3PARTY } ?: return AdoptResult.NotFound
            is Error -> {
                logcat(ERROR) { "Sync-ScopedToken: failed to check existing credentials: ${r.reason}" }
                return AdoptResult.Failed(r.copy(code = GENERIC_ERROR.code))
            }
        }
        logcat { "Sync-ScopedToken: 3party credential already exists on server" }
        val encryptedSp = existing.encryptedCredential ?: return AdoptResult.Failed(
            Error(reason = "CreateThirdPartyCredential: server returned 3party credential without encryptedCredential; cannot recover SP"),
        )
        val decrypted = when (
            val r = decryptSpEnvelope(
                encryptedSp,
                inputs.primaryKey,
                inputs.hkdfSalt,
                errorPrefix = "CreateThirdPartyCredential",
            )
        ) {
            is Success -> r.data
            is Error -> return AdoptResult.Failed(r)
        }
        logcat { "Sync-ScopedToken: decrypted SP from server, storing locally" }
        syncStore.scopedPassword = ScopedPassword(decrypted)
        return AdoptResult.Adopted
    }

    private fun mintAndPostNewCredential(inputs: CreateInputs): Result<Boolean> {
        logcat { "Sync-ScopedToken: generating new 3party credential" }

        // Re-auth against the existing ddg credential's twice_hashed_password.
        val preLogin = kotlin.runCatching {
            nativeLib.prepareForLogin(inputs.primaryKey).also {
                it.checkResult("CreateThirdPartyCredential: prepareForLogin failed")
            }
        }.getOrElse { return it.asErrorResult() }

        // Reuse the account-key KDF to mint the SP. `spAccountKeys.primaryKey` here is the SP,
        // not the account MP.
        val newSp = kotlin.runCatching {
            nativeLib.generateAccountKeys(userId = inputs.userId).also {
                it.checkResult("CreateThirdPartyCredential: SP key generation failed")
            }.primaryKey
        }.getOrElse { return it.asErrorResult() }

        val encryptedSpCredential = when (val r = encryptSpForDdgCredential(newSp, inputs.primaryKey, inputs.hkdfSalt)) {
            is Success -> r.data
            is Error -> return r
        }
        val reEncryptedKeys = when (val r = reEncryptExistingDdgKeysFor3party(inputs.token, newSp, inputs.hkdfSalt)) {
            is Success -> r.data
            is Error -> return r
        }
        val credentialHashedPassword = kotlin.runCatching {
            // HKDF-SHA-256(SP_raw, salt=user_id_utf8, info="Password", 32) → base64url, per
            // Encryption Algorithms TD (Asana 1214802412121967). Pinned in SyncJweCryptoTdVectorsTest.
            syncJweCrypto.hkdfDeriveBase64Url(newSp, inputs.hkdfSalt, "Password", 32)
        }.getOrElse { return it.asLoggedError("CreateThirdPartyCredential: failed to derive credential hashed password") }

        val request = CreateAccessCredentialRequest(
            hashedPassword = preLogin.passwordHash,
            credentialHashedPassword = credentialHashedPassword,
            encrypted3partyCredential = encryptedSpCredential,
            keys = reEncryptedKeys.ifEmpty { null },
        )
        return postCreateAccessCredential(inputs, request, newSp)
    }

    /** Encrypt the SP with the DDG MEK (AES-GCM-JWE-dir, kid="ddg"). The kid tells the decrypting
     *  client to derive the MEK from the account's MP. */
    private fun encryptSpForDdgCredential(newSpBase64: String, primaryKey: String, hkdfSalt: ByteArray): Result<String> {
        val ddgMek = kotlin.runCatching { syncJweCrypto.hkdfDeriveBytes(primaryKey, hkdfSalt, "Main Key", 32) }
            .getOrElse { return it.asLoggedError("CreateThirdPartyCredential: failed to derive DDG MEK") }
        // SP travels on the wire as base64url; convert from local standard base64 first.
        val spRawBytes = kotlin.runCatching { Base64.decode(newSpBase64, Base64.NO_WRAP) }
            .getOrElse { return it.asLoggedError("CreateThirdPartyCredential: failed to decode SP bytes") }
        val spBase64Url = Base64.encodeToString(spRawBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        return kotlin.runCatching {
            Success(syncJweCrypto.jweEncryptSymmetric(spBase64Url.toByteArray(Charsets.UTF_8), ddgMek, kid = CREDENTIAL_ID_DDG))
        }.getOrElse { it.asLoggedError("CreateThirdPartyCredential: failed to encrypt SP") }
    }

    /**
     * Re-encrypt every ddg-wrapped protected key for the 3party credential. The two sides wrap
     * differently by design (Encryption Algorithms TD, Asana 1214802412121967):
     *   - ddg-side: libsodium-secretbox(privateKey, secretKey). Native-only, matches duck.ai's
     *     EncryptWithSyncMasterKeyHandler.
     *   - 3party-side: RFC 7516 JWE compact, kid="3party", AES-256-GCM,
     *     key = HKDF(SP, salt=user_id, info="Main Key"). Cross-platform readable.
     */
    private fun reEncryptExistingDdgKeysFor3party(
        token: String,
        newSpBase64: String,
        hkdfSalt: ByteArray,
    ): Result<List<ProtectedKeyEntry>> {
        val spMek = kotlin.runCatching { syncJweCrypto.hkdfDeriveBytes(newSpBase64, hkdfSalt, "Main Key", 32) }
            .getOrElse { return it.asLoggedError("CreateThirdPartyCredential: failed to derive 3party MEK") }
        val accountSecretKey = syncStore.secretKey
            ?: return Error(reason = "CreateThirdPartyCredential: no account secret key for ddg-side decrypt")

        val ddgKeys = when (val r = syncApi.getProtectedKeys(token)) {
            is Success -> r.data.filter { it.encryptedWith == CREDENTIAL_ID_DDG }
            is Error -> {
                // Fail rather than create a credential without its protected keys.
                logcat(ERROR) { "Sync-ScopedToken: getKeys failed, aborting 3party credential creation: ${r.reason}" }
                return Error(reason = "CreateThirdPartyCredential: getKeys failed: ${r.reason}")
            }
        }
        logcat { "Sync-ScopedToken: ${ddgKeys.size} ddg key(s) to re-encrypt for 3party" }

        val results = ddgKeys.map { key ->
            kotlin.runCatching {
                // ddg key arrives base64url-encoded; convert to standard base64 then libsodium-decrypt.
                val encryptedBytes = Base64.decode(key.encryptedPrivateKey.removeUrlSafetyToRestoreB64(), Base64.NO_WRAP)
                val rawKeyBytes = nativeLib.decryptData(encryptedBytes, accountSecretKey).also {
                    it.checkResult("CreateThirdPartyCredential: failed to libsodium-decrypt ddg key ${key.kid}")
                }.decryptedData
                val reEncryptedJwe = syncJweCrypto.jweEncryptSymmetric(rawKeyBytes, spMek, kid = CREDENTIAL_ID_3PARTY)
                ProtectedKeyEntry(
                    kid = key.kid,
                    purpose = key.purpose,
                    encryptedWith = CREDENTIAL_ID_3PARTY,
                    encryptedPrivateKey = reEncryptedJwe,
                    publicKey = key.publicKey,
                )
            }
        }
        val firstFailure = results.firstOrNull { it.isFailure }?.exceptionOrNull()
        if (firstFailure != null) {
            logcat(ERROR) { "Sync-ScopedToken: failed to re-encrypt one or more protected keys: ${firstFailure.message}" }
            return Error(reason = "CreateThirdPartyCredential: re-encrypt key failed: ${firstFailure.message}")
        }
        return Success(results.map { it.getOrThrow() })
    }

    private fun postCreateAccessCredential(
        inputs: CreateInputs,
        request: CreateAccessCredentialRequest,
        newSpBase64: String,
    ): Result<Boolean> {
        logcat { "Sync-ScopedToken: posting 3party credential" }
        return when (val result = syncApi.createAccessCredential(inputs.token, CREDENTIAL_ID_3PARTY, request)) {
            is Success -> {
                logcat { "Sync-ScopedToken: 3party credential created" }
                syncStore.scopedPassword = ScopedPassword(newSpBase64)
                Success(true)
            }
            is Error -> {
                if (result.code == API_CODE.COUNT_LIMIT.code) {
                    // Spec (Asana 1214702966683640, "Setting up usage of a new scope"): on conflict,
                    // refetch and adopt if the credential is now present.
                    logcat { "Sync-ScopedToken: 409 conflict - another device created the credential first; adopting" }
                    return when (val adopted = tryAdoptExistingCredential(inputs)) {
                        AdoptResult.Adopted -> Success(true)
                        is AdoptResult.Failed -> adopted.error
                        AdoptResult.NotFound -> Error(reason = "CreateThirdPartyCredential: 409 conflict but credential missing on refetch")
                    }
                }
                logcat(ERROR) { "Sync-ScopedToken: failed to create 3party credential: ${result.reason}" }
                result
            }
        }
    }

    /** Decrypt an `encrypted_3party_credential` blob from the server into the standard-base64 SP. */
    private fun decryptSpEnvelope(
        encryptedSp: String,
        primaryKey: String,
        hkdfSalt: ByteArray,
        errorPrefix: String,
    ): Result<String> {
        val ddgMek = kotlin.runCatching { syncJweCrypto.hkdfDeriveBytes(primaryKey, hkdfSalt, "Main Key", 32) }
            .getOrElse { return it.asLoggedError("$errorPrefix: failed to derive DDG MEK") }
        val decryptedBase64Url = kotlin.runCatching {
            String(syncJweCrypto.jweDecryptSymmetric(encryptedSp, ddgMek), Charsets.UTF_8)
        }.getOrElse {
            logcat(ERROR) { "Sync-ScopedToken: failed to decrypt SP from server: ${it.message}" }
            return Error(reason = "$errorPrefix: failed to decrypt SP: ${it.message}")
        }
        return Success(base64UrlStringToStandardBase64(decryptedBase64Url))
    }

    override fun refresh(): Result<Boolean> {
        if (!syncFeature.canUseV2ConnectFlow().isEnabled()) {
            return Error(reason = "Scoped access credentials feature is disabled")
        }
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: return Error(reason = "RefreshThirdPartyCredential: not signed in")
        logcat { "Sync-ScopedToken: refreshing 3party credential from server" }

        val existing = when (val r = syncApi.getAccessCredentials(token)) {
            is Success -> r.data.find { it.id == CREDENTIAL_ID_3PARTY } ?: run {
                logcat { "Sync-ScopedToken: no 3party credential on server — nothing to refresh" }
                return Success(false)
            }
            is Error -> {
                logcat(ERROR) { "Sync-ScopedToken: failed to fetch access credentials: ${r.reason}" }
                return r.copy(code = GENERIC_ERROR.code)
            }
        }
        val encryptedSp = existing.encryptedCredential
            ?: return Error(reason = "RefreshThirdPartyCredential: server returned 3party credential without encryptedCredential")
        val primaryKey = syncStore.primaryKey
            ?: return Error(reason = "RefreshThirdPartyCredential: no primary key to derive DDG MEK")
        val userId = syncStore.userId
            ?: return Error(reason = "RefreshThirdPartyCredential: no userId for HKDF salt")

        val decrypted = when (
            val r = decryptSpEnvelope(
                encryptedSp,
                primaryKey,
                userId.toByteArray(Charsets.UTF_8),
                errorPrefix = "RefreshThirdPartyCredential",
            )
        ) {
            is Success -> r.data
            is Error -> return r
        }
        logcat { "Sync-ScopedToken: decrypted SP from server, storing locally" }
        syncStore.scopedPassword = ScopedPassword(decrypted)
        return Success(true)
    }

    override fun getRecoveryCode(): Result<String> {
        if (!syncFeature.canUseV2ConnectFlow().isEnabled()) {
            return Error(reason = "Get 3party Recovery Code: scoped access credentials feature is disabled")
        }
        val scopedPassword = syncStore.scopedPassword
            ?: return Error(reason = "Get 3party Recovery Code: no scoped password — create a 3party credential first")
        val userId = syncStore.userId
            ?: return Error(reason = "Get 3party Recovery Code: no userId")

        // 3party recovery code v2 (Asana 1214804486778180):
        //   { "recovery": { user_id, secret: <base64url of SP raw bytes>, cid: "3party", v: "2.0" } }
        // Inner JSON is base64url-encoded for transport.
        val encoded = kotlin.runCatching {
            val rawBytes = Base64.decode(scopedPassword.raw, Base64.NO_WRAP)
            val secretB64u = Base64.encodeToString(rawBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val payload = ThirdPartyRecoveryCodeWrapper(
                recovery = ThirdPartyRecoveryCode(
                    userId = userId,
                    secret = secretB64u,
                    cid = CREDENTIAL_ID_3PARTY,
                    v = RECOVERY_CODE_V2,
                ),
            )
            val json = recoveryCodeAdapter.toJson(payload)
            Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        }.getOrElse { return it.asLoggedError("Get 3party Recovery Code: failed to encode payload") }

        return Success(encoded)
    }

    private val recoveryCodeAdapter by lazy {
        Moshi.Builder().build().adapter(ThirdPartyRecoveryCodeWrapper::class.java)
    }
}
