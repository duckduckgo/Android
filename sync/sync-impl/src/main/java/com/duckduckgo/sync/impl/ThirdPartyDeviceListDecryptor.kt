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
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

/**
 * Decrypts `entries_v2`, preferring the cross-credential `device_info` when the unified device list read flag is on,
 * and falling back to the legacy `name`/`type` otherwise.
 *
 * Failure handling depends on the read flag:
 * - flag ON: we never auto-logout (decision 1216781918336689). Every device is rendered; anything that can't be decrypted (legacy or device_info)
 *            becomes an "Unknown device" placeholder the user can manually remove.
 * - flag OFF (today's behaviour): a 3p failure with an inconclusive refresh renders a fallback; ddg failures — and
 *   3p failures confirmed after a fresh scoped password — are marked undecryptable and logged out by the caller.
 */
@WorkerThread
interface ThirdPartyDeviceListDecryptor {
    fun decryptAll(entries: List<DeviceV2>): DecryptAllResult

    companion object {
        const val FALLBACK_TYPE_3PARTY = "Browser"
        const val FALLBACK_NAME = "Unknown device"
    }
}

data class DecryptAllResult(
    val decrypted: List<DecryptedDevice>,
    val undecryptable: List<String>,
)

@ContributesBinding(AppScope::class)
class RealThirdPartyDeviceListDecryptor @Inject constructor(
    private val deviceFieldDecryptor: DeviceFieldDecryptor,
    private val thirdPartyCredentialManager: ThirdPartyCredentialManager,
    private val deviceInfoDecryptor: DeviceInfoDecryptor,
    private val syncFeature: SyncFeature,
) : ThirdPartyDeviceListDecryptor {

    override fun decryptAll(entries: List<DeviceV2>): DecryptAllResult {
        if (entries.isEmpty()) return DecryptAllResult(emptyList(), emptyList())

        val readEnabled = syncFeature.canReadUnifiedDeviceList().isEnabled()

        val viaDeviceInfo = mutableListOf<DecryptedDevice>()
        val legacyEntries = mutableListOf<DeviceV2>()

        // a single session can decrypt every device's blob since device_info is encrypted using account-wide account_info key
        // only open one if the read flag is on and some entry actually carries device_info to decrypt
        val session = if (readEnabled && entries.any { !it.deviceInfo.isNullOrEmpty() }) openDeviceInfoSession() else null

        entries.forEach { entry ->
            val resolved = session?.let { decryptDeviceInfo(entry, it) }
            if (resolved != null) {
                viaDeviceInfo += resolved
            } else {
                // entries it can't resolve (e.g. feature flag off, or undecryptable device_info) fall through to the legacy path
                legacyEntries += entry
            }
        }

        // read flag on ⇒ never auto-logout; undecryptable entries become "Unknown device" placeholders instead
        val legacy = decryptLegacy(legacyEntries, neverAutoLogout = readEnabled)

        logcat(LogPriority.VERBOSE) {
            "Sync-UnifiedDevices: ${entries.size} devices → ${viaDeviceInfo.size} via device_info, " +
                "${legacy.viaLegacy.size} via legacy, ${legacy.fallbacks.size} placeholder, ${legacy.undecryptable.size} undecryptable"
        }
        return DecryptAllResult(
            decrypted = viaDeviceInfo + legacy.viaLegacy + legacy.fallbacks,
            undecryptable = legacy.undecryptable,
        )
    }

    private fun decryptDeviceInfo(
        entry: DeviceV2,
        session: DeviceInfoDecryptor.Session,
    ): DecryptedDevice? {
        val deviceId = entry.deviceId ?: return null
        val deviceInfo = entry.deviceInfo?.takeUnless { it.isEmpty() } ?: return null
        return when (val result = session.decrypt(deviceInfo)) {
            is Result.Success -> DecryptedDevice(deviceId = deviceId, name = result.data.name, type = result.data.type)
            is Result.Error -> {
                logcat(WARN) { "Sync-UnifiedDevices: $deviceId device_info decrypt failed, falling back to legacy: ${result.reason}" }
                null
            }
        }
    }

    /**
     * Called only with entries where device_info was not resolved.
     * Reasons for it not being resolved include where the FF is off, device_info is absent, or it is undecryptable.
     */
    private fun decryptLegacy(entries: List<DeviceV2>, neverAutoLogout: Boolean): LegacyDecryptResult {
        if (entries.isEmpty()) return LegacyDecryptResult(emptyList(), emptyList(), emptyList())

        val results = decryptWithRefreshRetry(entries)
        return classifyResults(results, neverAutoLogout)
    }

    /**
     * The per-entry legacy decrypt results, plus whether the 3p refresh (if one ran) produced a fresh scoped password.
     * That flag lets [classifyFailure] tell a confirmed failure from an inconclusive one.
     */
    private data class LegacyDecryptionAttempt(
        val results: List<Pair<DeviceV2, Result<DecryptedDevice>>>,
        val refreshGotFreshScopedPassword: Boolean,
    )

    /**
     * Decrypts every entry's legacy fields. A 3p failure can just be a stale scoped password rather than corruption,
     * so on any 3p failure we refresh the 3p credentials once and re-decrypt everything. Whether that refresh produced
     * a fresh scoped password is carried through so [classifyFailure] can tell a confirmed failure from an inconclusive one.
     */
    private fun decryptWithRefreshRetry(entries: List<DeviceV2>): LegacyDecryptionAttempt {
        val firstPass = entries.map { it to deviceFieldDecryptor.decrypt(it) }
        if (!firstPass.anyThirdPartyFailure()) return LegacyDecryptionAttempt(firstPass, refreshGotFreshScopedPassword = false)

        val refreshResult = thirdPartyCredentialManager.refresh()
        val refreshGotFreshScopedPassword = refreshResult is Result.Success && refreshResult.data
        logcat(WARN) { "Sync-UnifiedDevices: 3p decrypt failure, refreshed 3p credentials (freshSp=$refreshGotFreshScopedPassword)" }
        return LegacyDecryptionAttempt(entries.map { it to deviceFieldDecryptor.decrypt(it) }, refreshGotFreshScopedPassword)
    }

    private fun classifyResults(attempt: LegacyDecryptionAttempt, neverAutoLogout: Boolean): LegacyDecryptResult {
        val viaLegacy = mutableListOf<DecryptedDevice>()
        val fallbacks = mutableListOf<DecryptedDevice>()
        val undecryptable = mutableListOf<String>()
        attempt.results.forEach { (entry, result) ->
            when (result) {
                is Result.Success -> viaLegacy += result.data
                is Result.Error -> when (val outcome = classifyFailure(entry, result, attempt.refreshGotFreshScopedPassword, neverAutoLogout)) {
                    is FailureOutcome.Fallback -> fallbacks += outcome.device
                    is FailureOutcome.PermanentlyUndecryptable -> undecryptable += outcome.deviceId
                    FailureOutcome.Drop -> {}
                }
            }
        }
        return LegacyDecryptResult(viaLegacy, fallbacks, undecryptable)
    }

    private fun List<Pair<DeviceV2, Result<DecryptedDevice>>>.anyThirdPartyFailure(): Boolean =
        any { (entry, result) -> result is Result.Error && entry.credentialId == CREDENTIAL_ID_3PARTY }

    private fun openDeviceInfoSession(): DeviceInfoDecryptor.Session? =
        when (val result = deviceInfoDecryptor.openSession()) {
            is Result.Success -> result.data
            is Result.Error -> {
                logcat(WARN) { "Sync-UnifiedDevices: device_info session unavailable (code=${result.code}), using legacy: ${result.reason}" }
                null
            }
        }

    /**
     * Decides what to do with a device we couldn't decrypt.
     *
     * When [neverAutoLogout] is set (unified list read enabled), we always render an "Unknown device" placeholder —
     * we never log a device out over a decrypt failure (decision 1216781918336689).
     *
     * Otherwise (read flag off) we keep today's behaviour: a 3p failure with an inconclusive refresh renders a fallback
     * (can't tell stale-SP from corruption); anything else — ddg, or 3p confirmed after a fresh SP — is real corruption
     * and is marked for logout.
     */
    private fun classifyFailure(
        entry: DeviceV2,
        result: Result.Error,
        refreshGotFreshSp: Boolean,
        neverAutoLogout: Boolean,
    ): FailureOutcome {
        val id = entry.deviceId ?: return FailureOutcome.Drop

        if (neverAutoLogout) {
            logcat(WARN) { "Sync-UnifiedDevices: $id → Unknown device placeholder (${entry.credentialId}): ${result.reason}" }
            return FailureOutcome.Fallback(placeholderDevice(id, entry.credentialId))
        }

        if (entry.credentialId == CREDENTIAL_ID_3PARTY && !refreshGotFreshSp) {
            logcat(WARN) { "Sync-UnifiedDevices: $id → Unknown fallback (refreshing SP was inconclusive): ${result.reason}" }
            return FailureOutcome.Fallback(placeholderDevice(id, entry.credentialId))
        }
        logcat(WARN) { "Sync-UnifiedDevices: $id marked for logout (${entry.credentialId}): ${result.reason}" }
        return FailureOutcome.PermanentlyUndecryptable(id)
    }

    private fun placeholderDevice(deviceId: String, credentialId: String?): DecryptedDevice =
        DecryptedDevice(
            deviceId = deviceId,
            name = ThirdPartyDeviceListDecryptor.FALLBACK_NAME,
            type = if (credentialId == CREDENTIAL_ID_3PARTY) ThirdPartyDeviceListDecryptor.FALLBACK_TYPE_3PARTY else null,
        )

    /**
     * Outcome of the legacy per-credential pass, split so the caller can report and merge each bucket.
     * [viaLegacy] decrypted successfully; [fallbacks] rendered as an "Unknown device" placeholder; [undecryptable] marked for logout.
     */
    private data class LegacyDecryptResult(
        val viaLegacy: List<DecryptedDevice>,
        val fallbacks: List<DecryptedDevice>,
        val undecryptable: List<String>,
    )

    private sealed interface FailureOutcome {
        /** Render an "Unknown device" placeholder row. */
        data class Fallback(val device: DecryptedDevice) : FailureOutcome

        /** Confirmed undecryptable (ddg failure, or 3p failure after a fresh scoped password) */
        data class PermanentlyUndecryptable(val deviceId: String) : FailureOutcome

        /** No device id, so we can neither render nor log it out. */
        object Drop : FailureOutcome
    }
}
