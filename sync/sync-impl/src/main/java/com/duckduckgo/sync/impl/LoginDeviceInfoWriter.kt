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
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.UnifiedDeviceListPixel
import com.duckduckgo.sync.impl.pixels.toAccountInfoKeyWrapFailureReason
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

/**
 * Runs after a successful native (ddg) login to bring this device into the unified device list.
 * Best-effort by design: the login has already written the legacy device name/type, so a failure here must never fail the login.
 */
interface LoginDeviceInfoWriter {
    /**
     * Do the best-effort unified-device-list write for a freshly-completed ddg login. [loginResponseKeys] is the login response's keys[],
     * used to spot a 3party-only account_info key that this native device should re-wrap for ddg.
     */
    suspend fun onLogin(loginResponseKeys: List<ProtectedKeyEntry>?): Result<Unit>
}

@ContributesBinding(AppScope::class)
class RealLoginDeviceInfoWriter @Inject constructor(
    private val syncStore: SyncStore,
    private val syncApi: SyncApi,
    private val syncFeature: SyncFeature,
    private val protectedKeyUnwrapper: ProtectedKeyUnwrapper,
    private val nativeLib: SyncLib,
    private val deviceInfoMigrator: DeviceInfoMigrator,
    private val dispatchers: DispatcherProvider,
    private val syncPixels: SyncPixels,
) : LoginDeviceInfoWriter {

    /**
     *  If the account_info key exists wrapped only for 3party, it first adds the missing ddg wrap so this device can read the list
     *  It then runs the shared [DeviceInfoMigrator.ensureMigrated] to ensure the key exists and PATCH this device's device_info.
     *
     *  No-op when the write feature flag is off.
     */
    override suspend fun onLogin(loginResponseKeys: List<ProtectedKeyEntry>?): Result<Unit> = withContext(dispatchers.io()) {
        if (!syncFeature.canWriteDeviceInfo()) return@withContext Success(Unit)
        addDdgWrapIfThirdPartyOnly(loginResponseKeys)
        deviceInfoMigrator.ensureMigrated()
    }

    /** Silent no-op unless the account_info key is present, 3party-only, and we hold the scoped password to unwrap it. */
    private fun addDdgWrapIfThirdPartyOnly(loginResponseKeys: List<ProtectedKeyEntry>?) {
        val accountInfoKeys = loginResponseKeys.orEmpty().filter { it.purpose == SYNC_PURPOSE_ACCOUNT_INFO }
        if (accountInfoKeys.isEmpty()) {
            logcat { "Sync-UnifiedDevices: no account_info key in login response; migration will create it" }
            return
        }

        // if encrypted with DDG, nothing to do as already ddg-readable
        if (accountInfoKeys.any { it.encryptedWith == CREDENTIAL_ID_DDG }) {
            logcat { "Sync-UnifiedDevices: account_info already has a ddg wrap; no re-wrap needed" }
            return
        }

        val threeParty = accountInfoKeys.firstOrNull { it.encryptedWith == CREDENTIAL_ID_3PARTY }
        if (threeParty == null) {
            logcat { "Sync-UnifiedDevices: account_info has no ddg or 3party wrap; nothing to re-wrap" }
            return
        }
        if (syncStore.scopedPassword?.raw.isNullOrEmpty()) {
            logcat { "Sync-UnifiedDevices: account_info is 3party-only but no scoped password held; skipping ddg re-wrap" }
            return
        }
        val token = syncStore.token.takeUnless { it.isNullOrEmpty() } ?: run {
            logcat { "Sync-UnifiedDevices: account_info is 3party-only but no token; skipping ddg re-wrap" }
            return
        }
        val accountSecretKey = syncStore.secretKey?.takeUnless { it.isEmpty() } ?: run {
            logcat { "Sync-UnifiedDevices: account_info is 3party-only but no account secret key; skipping ddg re-wrap" }
            return
        }

        logcat { "Sync-UnifiedDevices: account_info is 3party-only; re-wrapping for ddg (kid=${threeParty.kid})" }
        val ddgEntry = when (val result = buildDdgWrap(threeParty, accountSecretKey)) {
            is Success -> result.data
            is Error -> {
                logcat(ERROR) { "Sync-UnifiedDevices: failed to build ddg wrap for account_info: ${result.reason}" }
                syncPixels.fireUnifiedDeviceListPixel(
                    UnifiedDeviceListPixel.AccountInfoKeyWrapFailed(
                        UnifiedDeviceListPixel.AccountInfoKeyWrapFailureReason.UNWRAP_FAILED,
                    ),
                )
                return
            }
        }

        // Add the missing ddg wrap via set-if-absent, reusing the existing key's kid.
        // Best-effort; any failure just leaves this device's reads degraded until the key converges; it never blocks the device_info write below.
        when (val result = syncApi.setKeysIfAbsent(token, SYNC_PURPOSE_ACCOUNT_INFO, listOf(ddgEntry))) {
            is Success -> when (result.data) {
                SetKeysIfAbsentResult.Created -> {
                    logcat { "Sync-UnifiedDevices: added ddg wrap for account_info (kid=${threeParty.kid})" }
                    syncPixels.fireUnifiedDeviceListPixel(UnifiedDeviceListPixel.AccountInfoKeyWrapSuccess)
                }
                // 200: this encrypted_with was already stored; this device did not add a wrap
                is SetKeysIfAbsentResult.Existing -> {
                    logcat { "Sync-UnifiedDevices: ddg account_info wrap already present (kid=${threeParty.kid})" }
                }
                SetKeysIfAbsentResult.ExistsFetchRequired -> {
                    logcat(ERROR) { "Sync-UnifiedDevices: server rejected the ddg account_info wrap" }
                    syncPixels.fireUnifiedDeviceListPixel(
                        UnifiedDeviceListPixel.AccountInfoKeyWrapFailed(
                            UnifiedDeviceListPixel.AccountInfoKeyWrapFailureReason.REQUEST_FAILED,
                        ),
                    )
                }
            }
            is Error -> {
                logcat(ERROR) { "Sync-UnifiedDevices: set-if-absent of ddg account_info wrap failed: ${result.reason}" }
                syncPixels.fireUnifiedDeviceListPixel(
                    UnifiedDeviceListPixel.AccountInfoKeyWrapFailed(result.toAccountInfoKeyWrapFailureReason()),
                )
            }
        }
    }

    /** Unwrap [source] with the scoped password and re-wrap the raw private key for `ddg`, preserving its kid and public key. */
    private fun buildDdgWrap(source: ProtectedKeyEntry, accountSecretKey: String): Result<ProtectedKeyEntry> {
        val rawPrivateKeyBytes = when (val result = protectedKeyUnwrapper.unwrap(source)) {
            is Success -> result.data
            is Error -> return result
        }
        val wireEncryptedPrivateKey = when (val result = ddgWrapPrivateKey(rawPrivateKeyBytes, accountSecretKey, nativeLib, "LoginReWrap")) {
            is Success -> result.data
            is Error -> return result
        }
        return Success(
            ProtectedKeyEntry(
                kid = source.kid,
                purpose = SYNC_PURPOSE_ACCOUNT_INFO,
                encryptedWith = CREDENTIAL_ID_DDG,
                encryptedPrivateKey = wireEncryptedPrivateKey,
                publicKey = source.publicKey,
            ),
        )
    }
}
