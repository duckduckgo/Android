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
     * (ddg always, 3party if a scoped password exists), and registers it with the server via set-if-absent.
     *
     * If another device already registered a key for this purpose first, the local mint is discarded and the server's key is adopted instead.
     * The winning public key is returned in [AccountInfoKeyResult].
     */
    suspend fun ensureKeyRegistered(): Result<AccountInfoKeyResult>
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
    private val dispatchers: DispatcherProvider,
) : AccountInfoKeyManager {

    override suspend fun ensureKeyRegistered(): Result<AccountInfoKeyResult> = withContext(dispatchers.io()) {
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: return@withContext Error(reason = "CreateAccountInfoKey: not signed in")
        val accountSecretKey = syncStore.secretKey
            ?: return@withContext Error(reason = "CreateAccountInfoKey: no account secret key")

        val minted = when (val result = mintKeypair(accountSecretKey)) {
            is Success -> result.data
            is Error -> return@withContext result
        }

        val entries = when (val result = wrapForCredentials(minted)) {
            is Success -> result.data
            is Error -> return@withContext result
        }

        logcat { "Sync-UnifiedDevices: registering $SYNC_PURPOSE_ACCOUNT_INFO key (kid=${minted.entry.kid}, wraps=${entries.size})" }
        when (val result = syncApi.setKeysIfAbsent(token, SYNC_PURPOSE_ACCOUNT_INFO, entries)) {
            is Success -> onSetIfAbsentSuccess(minted, entries.size, result.data)
            is Error -> {
                logcat(ERROR) { "Sync-UnifiedDevices: setKeysIfAbsent failed: ${result.reason}" }
                result
            }
        }
    }

    private fun mintKeypair(accountSecretKey: String): Result<MintedProtectedKey> =
        mintDdgWrappedProtectedKey(
            purpose = SYNC_PURPOSE_ACCOUNT_INFO,
            accountSecretKey = accountSecretKey,
            syncJweCrypto = syncJweCrypto,
            nativeLib = nativeLib,
            errorPrefix = "CreateAccountInfoKey",
            keySizeBits = RSA_KEY_SIZE_ACCOUNT_INFO,
        )

    private fun wrapForCredentials(minted: MintedProtectedKey): Result<List<ProtectedKeyEntry>> {
        val entries = mutableListOf(minted.entry)
        val scopedPassword = syncStore.scopedPassword
        val userId = syncStore.userId
        if (scopedPassword != null && userId != null) {
            entries += when (val result = thirdPartyKeyWrapper.wrap(minted, SYNC_PURPOSE_ACCOUNT_INFO, scopedPassword, userId)) {
                is Success -> result.data
                is Error -> return result
            }
        }
        return Success(entries)
    }

    private fun onSetIfAbsentSuccess(
        minted: MintedProtectedKey,
        wrapsSent: Int,
        outcome: SetKeysIfAbsentResult,
    ): Result<AccountInfoKeyResult> {
        return when (outcome) {
            SetKeysIfAbsentResult.Created -> {
                logcat { "Sync-UnifiedDevices: our key won (kid=${minted.entry.kid})" }
                Success(
                    AccountInfoKeyResult(kid = minted.entry.kid, publicKey = minted.entry.publicKey, created = true, wrapsSent = wrapsSent),
                )
            }
            is SetKeysIfAbsentResult.Existing -> {
                logcat { "Sync-UnifiedDevices: another device's key won (kid=${outcome.kid}); discarding local mint" }
                Success(
                    AccountInfoKeyResult(kid = outcome.kid, publicKey = outcome.publicKey, created = false, wrapsSent = wrapsSent),
                )
            }
        }
    }
}
