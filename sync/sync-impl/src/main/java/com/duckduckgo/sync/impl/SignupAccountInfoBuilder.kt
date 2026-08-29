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

import androidx.annotation.WorkerThread
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.UnifiedDeviceListPixel.AccountInfoKeyCreateFailed
import com.duckduckgo.sync.impl.pixels.UnifiedDeviceListPixel.AccountInfoKeyCreateFailureReason
import com.duckduckgo.sync.impl.pixels.UnifiedDeviceListPixel.DeviceInfoWriteFailureReason
import com.duckduckgo.sync.impl.pixels.UnifiedDeviceListPixel.OwnRowDeviceInfoFirstWriteFailed
import com.duckduckgo.sync.store.AccountInfoPublicKey
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

/** The unified-device-list additions to send in a signup request, plus the public key the caller caches once signup succeeds. */
data class SignupAccountInfo(
    val deviceInfo: String,
    val keys: List<ProtectedKeyEntry>,
    val publicKey: AccountInfoPublicKey,
)

/**
 * Prepares the `account_info` key + encrypted `device_info` to embed in a signup request, so the account, its key and this device's
 * device_info are created atomically. Signup can do this one-shot (unlike login) because it mints the keypair locally and therefore holds
 * the public key up front.
 */
@WorkerThread
interface SignupAccountInfoBuilder {
    /**
     * Returns the additions to include in the signup body, or null when they should be omitted — the write feature is off, or minting /
     * encryption failed. Null is not an error: the caller signs up without them (best-effort) and migration/login backfills later.
     * [accountSecretKey] is the freshly-generated account secret key (not yet in the store).
     */
    fun build(accountSecretKey: String, deviceName: String, deviceType: String): SignupAccountInfo?
}

@ContributesBinding(AppScope::class)
class RealSignupAccountInfoBuilder @Inject constructor(
    private val syncFeature: SyncFeature,
    private val accountInfoKeyManager: AccountInfoKeyManager,
    private val deviceInfoEncryptor: DeviceInfoEncryptor,
    private val syncPixels: SyncPixels,
) : SignupAccountInfoBuilder {

    override fun build(accountSecretKey: String, deviceName: String, deviceType: String): SignupAccountInfo? {
        if (!syncFeature.canWriteUnifiedDeviceList().isEnabled()) return null

        val minkedKey = when (val result = accountInfoKeyManager.mintUnregistered(accountSecretKey)) {
            is Success -> result.data
            is Error -> {
                logcat(ERROR) { "Sync-UnifiedDevices: signup account_info mint failed, signing up without it: ${result.reason}" }
                syncPixels.fireUnifiedDeviceListPixel(
                    AccountInfoKeyCreateFailed(AccountInfoKeyCreateFailureReason.MINT_FAILED),
                )
                return null
            }
        }
        val publicJwk = minkedKey.entry.publicKey ?: run {
            logcat(ERROR) { "Sync-UnifiedDevices: signup minted account_info key has no public key; signing up without it" }
            return null
        }
        val publicKey = AccountInfoPublicKey(keyId = minkedKey.entry.kid, modulus = publicJwk.n, exponent = publicJwk.e)

        val deviceInfo = when (val result = deviceInfoEncryptor.encrypt(deviceName, deviceType, publicKey)) {
            is Success -> result.data
            is Error -> {
                logcat(ERROR) { "Sync-UnifiedDevices: signup device_info encryption failed, signing up without it: ${result.reason}" }
                syncPixels.fireUnifiedDeviceListPixel(
                    OwnRowDeviceInfoFirstWriteFailed(DeviceInfoWriteFailureReason.ENCRYPT_FAILED),
                )
                return null
            }
        }
        return SignupAccountInfo(deviceInfo = deviceInfo, keys = listOf(minkedKey.entry), publicKey = publicKey)
    }
}
