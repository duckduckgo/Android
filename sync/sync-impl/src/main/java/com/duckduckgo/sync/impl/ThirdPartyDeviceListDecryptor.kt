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
import com.duckduckgo.sync.store.SyncStore
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
    /**
     * @param thisDeviceId this device's sync id, used to report back whether our own `device_info` resolved
     */
    fun decryptAll(entries: List<DeviceV2>, thisDeviceId: String?): DecryptAllResult

    companion object {
        const val FALLBACK_TYPE_3PARTY = "Browser"
        const val FALLBACK_NAME = "Unknown device"
    }
}

data class DecryptAllResult(
    val decrypted: List<DecryptedDevice>,
    val undecryptable: List<String>,
    val thisDeviceInfoNeedsRepair: Boolean = false,
    val keyUnavailableReason: AccountInfoKeyUnavailableReason? = null,
    val ownDeviceReadOutcome: OwnDeviceReadOutcome? = null,
    val otherRowFailedDecryptionCredentials: Set<DeviceCredential> = emptySet(),
    val otherRowPlaceholderCredentials: Set<DeviceCredential> = emptySet(),
)

sealed interface OwnDeviceReadOutcome {
    data object ResolvedDeviceInfo : OwnDeviceReadOutcome
    data class ResolvedLegacy(val reason: DeviceInfoReadFailureReason) : OwnDeviceReadOutcome
    data class ResolvedPlaceholder(val reason: DeviceInfoReadFailureReason) : OwnDeviceReadOutcome
}

enum class DeviceInfoReadFailureReason {
    NOT_PUBLISHED_YET,
    BLOB_ABSENT,
    BLOB_DECRYPT_FAILED,
}

enum class DeviceCredential {
    DDG,
    THIRD_PARTY,
    NONE,
}

@ContributesBinding(AppScope::class)
class RealThirdPartyDeviceListDecryptor @Inject constructor(
    private val deviceFieldDecryptor: DeviceFieldDecryptor,
    private val thirdPartyCredentialManager: ThirdPartyCredentialManager,
    private val deviceInfoDecryptor: DeviceInfoDecryptor,
    private val syncFeature: SyncFeature,
    private val syncStore: SyncStore,
) : ThirdPartyDeviceListDecryptor {

    override fun decryptAll(entries: List<DeviceV2>, thisDeviceId: String?): DecryptAllResult {
        if (entries.isEmpty()) return DecryptAllResult(emptyList(), emptyList())

        val readEnabled = syncFeature.canReadUnifiedDeviceList().isEnabled()
        // Opening the session even when all blobs are absent distinguishes a missing blob from a key that could not be obtained.
        val sessionOutcome = if (readEnabled) deviceInfoDecryptor.openSession() else null
        val session = (sessionOutcome as? DeviceInfoSessionResult.Available)?.session

        val viaDeviceInfo = resolveViaDeviceInfo(entries, session)
        val legacy = decryptLegacy(viaDeviceInfo.unresolved, neverAutoLogout = readEnabled)

        logcat(LogPriority.VERBOSE) {
            "Sync-UnifiedDevices: ${entries.size} devices → ${viaDeviceInfo.resolved.size} via device_info, " +
                "${legacy.viaLegacy.size} via legacy, ${legacy.fallbacks.size} placeholder, ${legacy.undecryptable.size} undecryptable"
        }

        val ownDeviceReadOutcome = session?.let {
            ownDeviceReadOutcome(entries, thisDeviceId, viaDeviceInfo.resolved, legacy, viaDeviceInfo.failedDecryptIds)
        }
        val credentialsById = otherRowCredentialsById(entries, excludingDeviceId = thisDeviceId)

        return DecryptAllResult(
            decrypted = viaDeviceInfo.resolved + legacy.viaLegacy + legacy.fallbacks,
            undecryptable = legacy.undecryptable,
            thisDeviceInfoNeedsRepair = ownDeviceReadOutcome.needsRepair(),
            keyUnavailableReason = (sessionOutcome as? DeviceInfoSessionResult.Unavailable)?.reason,
            ownDeviceReadOutcome = ownDeviceReadOutcome,
            otherRowFailedDecryptionCredentials = viaDeviceInfo.failedDecryptIds.mapNotNull(credentialsById::get).toSet(),
            otherRowPlaceholderCredentials = legacy.fallbacks.mapNotNull { credentialsById[it.deviceId] }.toSet(),
        )
    }

    /**
     * Tries `device_info` for every entry. [unresolved] is everything that must fall through to the legacy path
     * (no session, blob absent, or blob present but undecryptable). [failedDecryptIds] is only the last of those.
     */
    private fun resolveViaDeviceInfo(
        entries: List<DeviceV2>,
        session: DeviceInfoDecryptor.Session?,
    ): DeviceInfoDecryptResult {
        if (session == null) {
            return DeviceInfoDecryptResult(resolved = emptyList(), unresolved = entries, failedDecryptIds = emptySet())
        }

        val resolved = mutableListOf<DecryptedDevice>()
        val unresolved = mutableListOf<DeviceV2>()
        val failedDecryptIds = mutableSetOf<String>()
        entries.forEach { entry ->
            val device = decryptDeviceInfo(entry, session)
            if (device != null) {
                resolved += device
            } else {
                if (!entry.deviceInfo.isNullOrEmpty()) {
                    entry.deviceId?.let(failedDecryptIds::add)
                }
                unresolved += entry
            }
        }
        return DeviceInfoDecryptResult(resolved, unresolved, failedDecryptIds)
    }

    private fun otherRowCredentialsById(
        entries: List<DeviceV2>,
        excludingDeviceId: String?,
    ): Map<String, DeviceCredential> = entries.mapNotNull { entry ->
        if (entry.deviceId == excludingDeviceId) return@mapNotNull null
        val id = entry.deviceId ?: return@mapNotNull null
        val credential = entry.credentialId.toDeviceCredentialOrNull() ?: return@mapNotNull null
        id to credential
    }.toMap()

    private fun OwnDeviceReadOutcome?.needsRepair(): Boolean = when (this) {
        is OwnDeviceReadOutcome.ResolvedLegacy -> reason != DeviceInfoReadFailureReason.NOT_PUBLISHED_YET
        is OwnDeviceReadOutcome.ResolvedPlaceholder -> reason != DeviceInfoReadFailureReason.NOT_PUBLISHED_YET
        OwnDeviceReadOutcome.ResolvedDeviceInfo, null -> false
    }

    private fun ownDeviceReadOutcome(
        entries: List<DeviceV2>,
        thisDeviceId: String?,
        viaDeviceInfo: List<DecryptedDevice>,
        legacy: LegacyDecryptResult,
        failedDeviceInfoIds: Set<String>,
    ): OwnDeviceReadOutcome? {
        val ownDeviceId = thisDeviceId ?: return null
        val ownEntry = entries.firstOrNull { it.deviceId == ownDeviceId } ?: return null
        if (viaDeviceInfo.any { it.deviceId == ownDeviceId }) return OwnDeviceReadOutcome.ResolvedDeviceInfo

        val reason = when {
            ownEntry.deviceId in failedDeviceInfoIds -> DeviceInfoReadFailureReason.BLOB_DECRYPT_FAILED
            syncStore.unifiedDeviceListMigratedForUserId == syncStore.userId -> DeviceInfoReadFailureReason.BLOB_ABSENT
            else -> DeviceInfoReadFailureReason.NOT_PUBLISHED_YET
        }
        return if (legacy.viaLegacy.any { it.deviceId == ownDeviceId }) {
            OwnDeviceReadOutcome.ResolvedLegacy(reason)
        } else {
            OwnDeviceReadOutcome.ResolvedPlaceholder(reason)
        }
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

    private data class DeviceInfoDecryptResult(
        val resolved: List<DecryptedDevice>,
        val unresolved: List<DeviceV2>,
        val failedDecryptIds: Set<String>,
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

    private fun String?.toDeviceCredentialOrNull(): DeviceCredential? = when (this) {
        CREDENTIAL_ID_DDG -> DeviceCredential.DDG
        CREDENTIAL_ID_3PARTY -> DeviceCredential.THIRD_PARTY
        null -> DeviceCredential.NONE
        else -> null
    }

    private sealed interface FailureOutcome {
        /** Render an "Unknown device" placeholder row. */
        data class Fallback(val device: DecryptedDevice) : FailureOutcome

        /** Confirmed undecryptable (ddg failure, or 3p failure after a fresh scoped password) */
        data class PermanentlyUndecryptable(val deviceId: String) : FailureOutcome

        /** No device id, so we can neither render nor log it out. */
        object Drop : FailureOutcome
    }
}
