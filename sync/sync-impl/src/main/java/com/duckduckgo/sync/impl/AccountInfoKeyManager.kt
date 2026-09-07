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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.UnifiedDeviceListPixel
import com.duckduckgo.sync.impl.pixels.toAccountInfoKeyAdoptFailureReason
import com.duckduckgo.sync.impl.pixels.toAccountInfoKeyCreateFailureReason
import com.duckduckgo.sync.store.AccountInfoPublicKey
import com.duckduckgo.sync.store.ScopedPassword
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

/**
 * The unified-devices `account_info` keypair MUST be created as RSA-3072.
 * Implementations must not assume a fixed modulus size when importing or validating this key.
 */
internal const val RSA_KEY_SIZE_ACCOUNT_INFO = 3072

/**
 * Owns the account-wide `account_info` protected key — the RSA keypair that will encrypt the unified cross-credential `device_info` blob.
 */
interface AccountInfoKeyManager {

    /**
     * Mints a fresh `account_info` keypair, wraps it for every credential this account currently holds
     * (ddg always, 3party whenever the account has that credential), and registers it with the server via set-if-absent.
     *
     * If another device already registered a key for this purpose first, the local mint is discarded and the server's key is adopted instead.
     * The winning public key is returned in [AccountInfoKeyResult] and cached in [SyncStore.accountInfoPublicKey].
     */
    suspend fun ensureKeyRegistered(): Result<AccountInfoKeyResult>

    /**
     * Mint a new `account_info` keypair wrapped for `ddg` only (a brand-new account has no scoped password yet) without registering it on the server
     */
    fun mintUnregistered(accountSecretKey: String): Result<MintedProtectedKey>
}

data class AccountInfoKeyResult(
    val kid: String,
    val publicKey: RsaJwk?,
    val created: Boolean,
    val wrapsSent: Int,
)

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealAccountInfoKeyManager @Inject constructor(
    private val syncStore: SyncStore,
    private val syncApi: SyncApi,
    private val syncJweCrypto: SyncJweCrypto,
    private val nativeLib: SyncLib,
    private val thirdPartyKeyWrapper: ThirdPartyKeyWrapper,
    private val thirdPartyCredentialManager: ThirdPartyCredentialManager,
    private val dispatchers: DispatcherProvider,
    private val syncPixels: SyncPixels,
) : AccountInfoKeyManager {

    override suspend fun ensureKeyRegistered(): Result<AccountInfoKeyResult> = withContext(dispatchers.io()) {
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: return@withContext Error(reason = "CreateAccountInfoKey: not signed in")
        val accountSecretKey = syncStore.secretKey
            ?: return@withContext Error(reason = "CreateAccountInfoKey: no account secret key")

        val minted = when (val result = mintUnregistered(accountSecretKey)) {
            is Success -> result.data
            is Error -> {
                syncPixels.fireUnifiedDeviceListPixel(
                    UnifiedDeviceListPixel.AccountInfoKeyCreateFailed(
                        UnifiedDeviceListPixel.AccountInfoKeyCreateFailureReason.MINT_FAILED,
                    ),
                )
                return@withContext result
            }
        }

        val entries = when (val result = wrapForCredentials(minted)) {
            is Success -> result.data
            is Error -> return@withContext result
        }

        logcat { "Sync-UnifiedDevices: registering $SYNC_PURPOSE_ACCOUNT_INFO key (kid=${minted.entry.kid}, wraps=${entries.size})" }
        val registration = when (val result = syncApi.setKeysIfAbsent(token, SYNC_PURPOSE_ACCOUNT_INFO, entries)) {
            is Success -> onSetIfAbsentSuccess(token, minted, entries.size, result.data)
            is Error -> {
                logcat(ERROR) { "Sync-UnifiedDevices: setKeysIfAbsent failed: ${result.reason}" }
                syncPixels.fireUnifiedDeviceListPixel(
                    UnifiedDeviceListPixel.AccountInfoKeyCreateFailed(result.toAccountInfoKeyCreateFailureReason()),
                )
                result
            }
        }
        // Cache the winning public key once, covering all adopt paths (ours won, adopted from the response, or fetched after a conflict).
        if (registration is Success) {
            registration.data.publicKey?.let { syncStore.accountInfoPublicKey = it.toStoredKey(registration.data.kid) }
        }
        registration
    }

    override fun mintUnregistered(accountSecretKey: String): Result<MintedProtectedKey> =
        mintDdgWrappedProtectedKey(
            purpose = SYNC_PURPOSE_ACCOUNT_INFO,
            accountSecretKey = accountSecretKey,
            syncJweCrypto = syncJweCrypto,
            nativeLib = nativeLib,
            errorPrefix = "CreateAccountInfoKey",
            keySizeBits = RSA_KEY_SIZE_ACCOUNT_INFO,
        )

    /**
     * The spec requires the key to be wrapped for every credential the account holds. A cached scoped password is only evidence that this device
     * created, recovered or logged into the 3party credential — not that the account lacks one — so when it's absent we ask the server before
     * settling for a `ddg`-only key. Failing to recover one is not fatal: a `ddg`-only key still lets this device write `device_info`, and login
     * re-wraps for the missing credential.
     */
    private fun wrapForCredentials(minted: MintedProtectedKey): Result<List<ProtectedKeyEntry>> {
        val entries = mutableListOf(minted.entry)
        val scopedPassword = syncStore.scopedPassword ?: recoverScopedPassword()
        val userId = syncStore.userId
        if (scopedPassword != null && userId != null) {
            entries += when (val result = thirdPartyKeyWrapper.wrap(minted, SYNC_PURPOSE_ACCOUNT_INFO, scopedPassword, userId)) {
                is Success -> result.data
                is Error -> return result
            }
        }
        return Success(entries)
    }

    private fun recoverScopedPassword(): ScopedPassword? {
        logcat { "Sync-UnifiedDevices: no cached scoped password; checking whether the account has a 3party credential to wrap for" }
        return when (val result = thirdPartyCredentialManager.refresh()) {
            is Success -> if (result.data) {
                syncStore.scopedPassword
            } else {
                logcat { "Sync-UnifiedDevices: account has no 3party credential; wrapping $SYNC_PURPOSE_ACCOUNT_INFO for ddg only" }
                null
            }
            is Error -> {
                logcat(ERROR) { "Sync-UnifiedDevices: could not recover scoped password, wrapping for ddg only: ${result.reason}" }
                null
            }
        }
    }

    private fun onSetIfAbsentSuccess(
        token: String,
        minted: MintedProtectedKey,
        wrapsSent: Int,
        outcome: SetKeysIfAbsentResult,
    ): Result<AccountInfoKeyResult> {
        return when (outcome) {
            SetKeysIfAbsentResult.Created -> {
                logcat { "Sync-UnifiedDevices: our key won (kid=${minted.entry.kid})" }
                syncPixels.fireUnifiedDeviceListPixel(UnifiedDeviceListPixel.AccountInfoKeyCreateSuccess)
                Success(
                    AccountInfoKeyResult(kid = minted.entry.kid, publicKey = minted.entry.publicKey, created = true, wrapsSent = wrapsSent),
                )
            }
            is SetKeysIfAbsentResult.Existing -> {
                logcat { "Sync-UnifiedDevices: another device's key won (kid=${outcome.kid}); adopting from response" }
                syncPixels.fireUnifiedDeviceListPixel(UnifiedDeviceListPixel.AccountInfoKeyAdoptSuccess)
                Success(
                    AccountInfoKeyResult(kid = outcome.kid, publicKey = outcome.publicKey, created = false, wrapsSent = wrapsSent),
                )
            }
            SetKeysIfAbsentResult.ExistsFetchRequired -> adoptExistingFromServer(token, wrapsSent)
        }
    }

    /** The server has a key for this purpose but didn't return it (409, or a 200 shim); fetch and adopt it. */
    private fun adoptExistingFromServer(token: String, wrapsSent: Int): Result<AccountInfoKeyResult> {
        logcat { "Sync-UnifiedDevices: key already exists on server; fetching to adopt" }
        return when (val result = syncApi.getProtectedKeys(token)) {
            is Success -> {
                val existing = result.data.firstOrNull { it.purpose == SYNC_PURPOSE_ACCOUNT_INFO }
                    ?: return Error(reason = "CreateAccountInfoKey: server reported an existing key but none was found on fetch").also {
                        fireAdoptFailed(it)
                    }
                logcat { "Sync-UnifiedDevices: adopted existing key (kid=${existing.kid})" }
                syncPixels.fireUnifiedDeviceListPixel(UnifiedDeviceListPixel.AccountInfoKeyAdoptSuccess)
                Success(
                    AccountInfoKeyResult(kid = existing.kid, publicKey = existing.publicKey, created = false, wrapsSent = wrapsSent),
                )
            }
            is Error -> {
                logcat(ERROR) { "Sync-UnifiedDevices: failed to fetch keys to adopt existing: ${result.reason}" }
                fireAdoptFailed(result)
                result
            }
        }
    }

    private fun fireAdoptFailed(error: Error) {
        syncPixels.fireUnifiedDeviceListPixel(
            UnifiedDeviceListPixel.AccountInfoKeyAdoptFailed(error.toAccountInfoKeyAdoptFailureReason()),
        )
    }

    private fun RsaJwk.toStoredKey(kid: String) = AccountInfoPublicKey(keyId = kid, modulus = n, exponent = e)
}
