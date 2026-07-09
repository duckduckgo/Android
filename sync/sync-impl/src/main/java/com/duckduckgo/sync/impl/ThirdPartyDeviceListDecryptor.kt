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

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

/**
 * Decrypts `entries_v2` with one-shot refresh-on-3p-failure. Splits the result so the caller
 * can differentiate between those successfully decrypted and those with decryption failure.
 *
 * 3p failures only get classified as corrupted when `refresh()` returned `Success(true)` —
 * otherwise we can't tell stale-SP from corruption, so we render a fallback row instead of
 * logging the device out over a possibly-transient condition. ddg failures are always
 * corrupted (primary key doesn't drift).
 */
interface ThirdPartyDeviceListDecryptor {
    fun decryptAll(entries: List<DeviceV2>): DecryptAllResult

    companion object {
        const val FALLBACK_TYPE_3PARTY = "Browser"
        const val FALLBACK_NAME = "Unknown"
    }
}

data class DecryptAllResult(
    val decrypted: List<DecryptedDevice>,
    val undecryptable: List<String>,
)

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealThirdPartyDeviceListDecryptor @Inject constructor(
    private val deviceFieldDecryptor: DeviceFieldDecryptor,
    private val thirdPartyCredentialManager: ThirdPartyCredentialManager,
) : ThirdPartyDeviceListDecryptor {

    override fun decryptAll(entries: List<DeviceV2>): DecryptAllResult {
        if (entries.isEmpty()) return DecryptAllResult(emptyList(), emptyList())

        val firstPass = entries.map { it to deviceFieldDecryptor.decrypt(it) }
        var finalPass = firstPass

        // if there are 3rd party failures, allow a refresh attempt on 3p credentials and retry at decrypting
        var refreshGotFreshSp = false
        if (firstPass.anyThirdPartyFailure()) {
            val refreshResult = thirdPartyCredentialManager.refresh()
            refreshGotFreshSp = refreshResult is Result.Success && refreshResult.data
            finalPass = entries.map { it to deviceFieldDecryptor.decrypt(it) }
        }

        val decrypted = mutableListOf<DecryptedDevice>()
        val undecryptable = mutableListOf<String>()
        finalPass.forEach { (entry, result) ->
            when (result) {
                is Result.Success -> decrypted += result.data
                is Result.Error -> classifyFailure(entry, result, refreshGotFreshSp, decrypted, undecryptable)
            }
        }
        return DecryptAllResult(decrypted, undecryptable)
    }

    private fun List<Pair<DeviceV2, Result<DecryptedDevice>>>.anyThirdPartyFailure(): Boolean =
        any { (entry, result) -> result is Result.Error && entry.credentialId == CREDENTIAL_ID_3PARTY }

    private fun classifyFailure(
        entry: DeviceV2,
        result: Result.Error,
        refreshGotFreshSp: Boolean,
        decrypted: MutableList<DecryptedDevice>,
        undecryptable: MutableList<String>,
    ) {
        val id = entry.deviceId ?: return
        if (entry.credentialId == CREDENTIAL_ID_3PARTY && !refreshGotFreshSp) {
            logcat(WARN) { "DeviceListDecryptor: $id → Unknown fallback (refresh inconclusive): ${result.reason}" }
            decrypted += DecryptedDevice(
                deviceId = id,
                name = ThirdPartyDeviceListDecryptor.FALLBACK_NAME,
                type = ThirdPartyDeviceListDecryptor.FALLBACK_TYPE_3PARTY,
            )
        } else {
            logcat(WARN) { "DeviceListDecryptor: $id marked for logout (${entry.credentialId}): ${result.reason}" }
            undecryptable += id
        }
    }
}
