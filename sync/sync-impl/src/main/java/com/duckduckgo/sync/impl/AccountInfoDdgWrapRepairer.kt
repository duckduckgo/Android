/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.UnifiedDeviceListPixel
import com.duckduckgo.sync.impl.pixels.toAccountInfoKeyWrapFailureReason
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

@WorkerThread
interface AccountInfoDdgWrapRepairer {
    /**
     * Adds a `ddg` wrap for the existing `account_info` key identified by [kid].
     *
     * [entries] avoids another fetch when the caller already has the server's protected keys.
     */
    fun repair(
        kid: String,
        entries: List<ProtectedKeyEntry>? = null,
    ): Result<Unit>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAccountInfoDdgWrapRepairer @Inject constructor(
    private val syncStore: SyncStore,
    private val syncApi: SyncApi,
    private val protectedKeyUnwrapper: ProtectedKeyUnwrapper,
    private val nativeLib: SyncLib,
    private val syncPixels: SyncPixels,
) : AccountInfoDdgWrapRepairer {

    override fun repair(kid: String, entries: List<ProtectedKeyEntry>?): Result<Unit> {
        if (syncStore.scopedPassword?.raw.isNullOrEmpty()) {
            logcat { "Sync-UnifiedDevices: cannot repair ddg account_info wrap without a scoped password" }
            return Error(reason = "RepairAccountInfoDdgWrap: no scoped password")
        }
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() }
            ?: return Error(reason = "RepairAccountInfoDdgWrap: no token")
        val accountSecretKey = syncStore.secretKey?.takeUnless { it.isEmpty() }
            ?: return Error(reason = "RepairAccountInfoDdgWrap: no account secret key")

        val protectedKeys = entries ?: when (val result = syncApi.getProtectedKeys(token)) {
            is Success -> result.data
            is Error -> return failRequest(result)
        }
        val accountInfoKeys = protectedKeys.filter { it.purpose == SYNC_PURPOSE_ACCOUNT_INFO && it.kid == kid }
        if (accountInfoKeys.any { it.encryptedWith == CREDENTIAL_ID_DDG }) {
            logcat { "Sync-UnifiedDevices: account_info already has a ddg wrap (kid=$kid)" }
            return Success(Unit)
        }
        val thirdPartyEntry = accountInfoKeys.firstOrNull { it.encryptedWith == CREDENTIAL_ID_3PARTY }
            ?: return Error(reason = "RepairAccountInfoDdgWrap: no 3party wrap for kid=$kid")

        logcat { "Sync-UnifiedDevices: adding missing ddg wrap for account_info (kid=$kid)" }
        val ddgEntry = when (val result = buildDdgWrap(thirdPartyEntry, accountSecretKey)) {
            is Success -> result.data
            is Error -> {
                logcat(ERROR) { "Sync-UnifiedDevices: failed to build ddg wrap for account_info: ${result.reason}" }
                syncPixels.fireUnifiedDeviceListPixel(
                    UnifiedDeviceListPixel.AccountInfoKeyWrapFailed(
                        UnifiedDeviceListPixel.AccountInfoKeyWrapFailureReason.UNWRAP_FAILED,
                    ),
                )
                return result
            }
        }

        return when (val result = syncApi.setKeysIfAbsent(token, SYNC_PURPOSE_ACCOUNT_INFO, listOf(ddgEntry))) {
            is Error -> failRequest(result)
            is Success -> when (val outcome = result.data) {
                SetKeysIfAbsentResult.Created -> {
                    logcat { "Sync-UnifiedDevices: added ddg wrap for account_info (kid=$kid)" }
                    syncPixels.fireUnifiedDeviceListPixel(UnifiedDeviceListPixel.AccountInfoKeyWrapSuccess)
                    Success(Unit)
                }
                is SetKeysIfAbsentResult.Existing -> {
                    if (outcome.kid != kid) {
                        return failRequest(Error(reason = "RepairAccountInfoDdgWrap: server returned different kid"))
                    }
                    confirmDdgWrap(token, kid)
                }
                SetKeysIfAbsentResult.ExistsFetchRequired -> confirmDdgWrap(token, kid)
            }
        }
    }

    private fun confirmDdgWrap(token: String, kid: String): Result<Unit> {
        return when (val result = syncApi.getProtectedKeys(token)) {
            is Error -> failRequest(result)
            is Success -> {
                val ddgWrapExists = result.data.any {
                    it.purpose == SYNC_PURPOSE_ACCOUNT_INFO && it.kid == kid && it.encryptedWith == CREDENTIAL_ID_DDG
                }
                if (ddgWrapExists) {
                    logcat { "Sync-UnifiedDevices: confirmed ddg account_info wrap (kid=$kid)" }
                    Success(Unit)
                } else {
                    failRequest(Error(reason = "RepairAccountInfoDdgWrap: ddg wrap was not stored"))
                }
            }
        }
    }

    private fun buildDdgWrap(source: ProtectedKeyEntry, accountSecretKey: String): Result<ProtectedKeyEntry> {
        val rawPrivateKeyBytes = when (val result = protectedKeyUnwrapper.unwrap(source)) {
            is Success -> result.data
            is Error -> return result
        }
        val encryptedPrivateKey = when (
            val result = ddgWrapPrivateKey(rawPrivateKeyBytes, accountSecretKey, nativeLib, "RepairAccountInfoDdgWrap")
        ) {
            is Success -> result.data
            is Error -> return result
        }
        return Success(
            source.copy(
                encryptedWith = CREDENTIAL_ID_DDG,
                encryptedPrivateKey = encryptedPrivateKey,
            ),
        )
    }

    private fun failRequest(error: Error): Error {
        logcat(ERROR) { "Sync-UnifiedDevices: failed to add ddg account_info wrap: ${error.reason}" }
        syncPixels.fireUnifiedDeviceListPixel(
            UnifiedDeviceListPixel.AccountInfoKeyWrapFailed(error.toAccountInfoKeyWrapFailureReason()),
        )
        return error
    }
}
