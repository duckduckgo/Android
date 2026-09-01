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

package com.duckduckgo.sync.impl.pixels

import com.duckduckgo.sync.impl.API_CODE
import com.duckduckgo.sync.impl.AccountInfoKeyUnavailableReason
import com.duckduckgo.sync.impl.DeviceCredential
import com.duckduckgo.sync.impl.DeviceInfoReadFailureReason
import com.duckduckgo.sync.impl.DeviceInfoUpdateSource
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_ADOPT_FAILED
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_ADOPT_SUCCESS
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_CREATE_FAILED
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_CREATE_SUCCESS
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_UNAVAILABLE
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_WRAP_FAILED
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_WRAP_SUCCESS
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_OTHER_ROW_DEVICE_INFO_FAILED_DECRYPTION
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_OTHER_ROW_RESOLVED_PLACEHOLDER
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_OWN_ROW_DEVICE_INFO_FIRST_WRITE_FAILED
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_OWN_ROW_DEVICE_INFO_FIRST_WRITE_SUCCESS
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_OWN_ROW_DEVICE_INFO_REPAIR_FAILED
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_OWN_ROW_DEVICE_INFO_REPAIR_SUCCESS
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_OWN_ROW_DEVICE_INFO_UPDATE_FAILED
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_OWN_ROW_DEVICE_INFO_UPDATE_SUCCESS
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_OWN_ROW_RESOLVED_DEVICE_INFO
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_OWN_ROW_RESOLVED_LEGACY
import com.duckduckgo.sync.impl.pixels.SyncPixelName.SYNC_UNIFIED_DEVICES_OWN_ROW_RESOLVED_PLACEHOLDER

sealed class UnifiedDeviceListPixel(
    val pixelName: SyncPixelName,
    val isDaily: Boolean = true,
    val parameter: Pair<String, String>? = null,
    val tag: String? = null,
) {
    data object OwnRowResolvedDeviceInfo : UnifiedDeviceListPixel(
        SYNC_UNIFIED_DEVICES_OWN_ROW_RESOLVED_DEVICE_INFO,
    )

    data class OwnRowResolvedLegacy(val reason: DeviceInfoReadFailureReason) : UnifiedDeviceListPixel(
        pixelName = SYNC_UNIFIED_DEVICES_OWN_ROW_RESOLVED_LEGACY,
        parameter = REASON to reason.toPixelValue(),
        tag = dailyTag(SYNC_UNIFIED_DEVICES_OWN_ROW_RESOLVED_LEGACY, REASON, reason.toPixelValue()),
    )

    data class OwnRowResolvedPlaceholder(val reason: DeviceInfoReadFailureReason) : UnifiedDeviceListPixel(
        pixelName = SYNC_UNIFIED_DEVICES_OWN_ROW_RESOLVED_PLACEHOLDER,
        parameter = REASON to reason.toPixelValue(),
        tag = dailyTag(SYNC_UNIFIED_DEVICES_OWN_ROW_RESOLVED_PLACEHOLDER, REASON, reason.toPixelValue()),
    )

    data class AccountInfoKeyUnavailable(val reason: AccountInfoKeyUnavailableReason) : UnifiedDeviceListPixel(
        pixelName = SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_UNAVAILABLE,
        parameter = REASON to reason.toPixelValue(),
        tag = dailyTag(SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_UNAVAILABLE, REASON, reason.toPixelValue()),
    )

    data class OtherRowDeviceInfoFailedDecryption(val credential: DeviceCredential) : UnifiedDeviceListPixel(
        pixelName = SYNC_UNIFIED_DEVICES_OTHER_ROW_DEVICE_INFO_FAILED_DECRYPTION,
        parameter = CREDENTIAL to credential.toPixelValue(),
        tag = dailyTag(SYNC_UNIFIED_DEVICES_OTHER_ROW_DEVICE_INFO_FAILED_DECRYPTION, CREDENTIAL, credential.toPixelValue()),
    )

    data class OtherRowResolvedPlaceholder(val credential: DeviceCredential) : UnifiedDeviceListPixel(
        pixelName = SYNC_UNIFIED_DEVICES_OTHER_ROW_RESOLVED_PLACEHOLDER,
        parameter = CREDENTIAL to credential.toPixelValue(),
        tag = dailyTag(SYNC_UNIFIED_DEVICES_OTHER_ROW_RESOLVED_PLACEHOLDER, CREDENTIAL, credential.toPixelValue()),
    )

    data object AccountInfoKeyCreateSuccess : UnifiedDeviceListPixel(
        SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_CREATE_SUCCESS,
        isDaily = false,
    )

    data class AccountInfoKeyCreateFailed(val reason: AccountInfoKeyCreateFailureReason) : UnifiedDeviceListPixel(
        pixelName = SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_CREATE_FAILED,
        parameter = REASON to reason.toPixelValue(),
        tag = dailyTag(SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_CREATE_FAILED, REASON, reason.toPixelValue()),
    )

    data object AccountInfoKeyWrapSuccess : UnifiedDeviceListPixel(
        SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_WRAP_SUCCESS,
        isDaily = false,
    )

    data class AccountInfoKeyWrapFailed(val reason: AccountInfoKeyWrapFailureReason) : UnifiedDeviceListPixel(
        pixelName = SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_WRAP_FAILED,
        parameter = REASON to reason.toPixelValue(),
        tag = dailyTag(SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_WRAP_FAILED, REASON, reason.toPixelValue()),
    )

    data object AccountInfoKeyAdoptSuccess : UnifiedDeviceListPixel(
        SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_ADOPT_SUCCESS,
        isDaily = false,
    )

    data class AccountInfoKeyAdoptFailed(val reason: AccountInfoKeyAdoptFailureReason) : UnifiedDeviceListPixel(
        pixelName = SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_ADOPT_FAILED,
        parameter = REASON to reason.toPixelValue(),
        tag = dailyTag(SYNC_UNIFIED_DEVICES_ACCOUNT_INFO_KEY_ADOPT_FAILED, REASON, reason.toPixelValue()),
    )

    data object OwnRowDeviceInfoFirstWriteSuccess : UnifiedDeviceListPixel(
        SYNC_UNIFIED_DEVICES_OWN_ROW_DEVICE_INFO_FIRST_WRITE_SUCCESS,
        isDaily = false,
    )

    data class OwnRowDeviceInfoFirstWriteFailed(val reason: DeviceInfoWriteFailureReason) : UnifiedDeviceListPixel(
        pixelName = SYNC_UNIFIED_DEVICES_OWN_ROW_DEVICE_INFO_FIRST_WRITE_FAILED,
        parameter = REASON to reason.toPixelValue(),
        tag = dailyTag(SYNC_UNIFIED_DEVICES_OWN_ROW_DEVICE_INFO_FIRST_WRITE_FAILED, REASON, reason.toPixelValue()),
    )

    data object OwnRowDeviceInfoUpdateSuccess : UnifiedDeviceListPixel(
        SYNC_UNIFIED_DEVICES_OWN_ROW_DEVICE_INFO_UPDATE_SUCCESS,
        isDaily = false,
    )

    data class OwnRowDeviceInfoUpdateFailed(val reason: DeviceInfoWriteFailureReason) : UnifiedDeviceListPixel(
        pixelName = SYNC_UNIFIED_DEVICES_OWN_ROW_DEVICE_INFO_UPDATE_FAILED,
        parameter = REASON to reason.toPixelValue(),
        tag = dailyTag(SYNC_UNIFIED_DEVICES_OWN_ROW_DEVICE_INFO_UPDATE_FAILED, REASON, reason.toPixelValue()),
    )

    data object OwnRowDeviceInfoRepairSuccess : UnifiedDeviceListPixel(
        SYNC_UNIFIED_DEVICES_OWN_ROW_DEVICE_INFO_REPAIR_SUCCESS,
        isDaily = false,
    )

    data class OwnRowDeviceInfoRepairFailed(val reason: DeviceInfoWriteFailureReason) : UnifiedDeviceListPixel(
        pixelName = SYNC_UNIFIED_DEVICES_OWN_ROW_DEVICE_INFO_REPAIR_FAILED,
        parameter = REASON to reason.toPixelValue(),
        tag = dailyTag(SYNC_UNIFIED_DEVICES_OWN_ROW_DEVICE_INFO_REPAIR_FAILED, REASON, reason.toPixelValue()),
    )

    enum class AccountInfoKeyCreateFailureReason {
        MINT_FAILED,
        REQUEST_FAILED,
        RATE_LIMITED,
    }

    enum class AccountInfoKeyWrapFailureReason {
        UNWRAP_FAILED,
        REQUEST_FAILED,
        RATE_LIMITED,
    }

    enum class AccountInfoKeyAdoptFailureReason {
        KEYS_FETCH_FAILED,
        RATE_LIMITED,
    }

    enum class DeviceInfoWriteFailureReason {
        ENCRYPT_FAILED,
        REQUEST_FAILED,
        RATE_LIMITED,
    }

    companion object {
        fun writeSuccess(source: DeviceInfoUpdateSource): UnifiedDeviceListPixel = when (source) {
            DeviceInfoUpdateSource.FIRST_WRITE -> OwnRowDeviceInfoFirstWriteSuccess
            DeviceInfoUpdateSource.UPDATE -> OwnRowDeviceInfoUpdateSuccess
            DeviceInfoUpdateSource.REPAIR -> OwnRowDeviceInfoRepairSuccess
        }

        fun writeFailed(
            source: DeviceInfoUpdateSource,
            reason: DeviceInfoWriteFailureReason,
        ): UnifiedDeviceListPixel = when (source) {
            DeviceInfoUpdateSource.FIRST_WRITE -> OwnRowDeviceInfoFirstWriteFailed(reason)
            DeviceInfoUpdateSource.UPDATE -> OwnRowDeviceInfoUpdateFailed(reason)
            DeviceInfoUpdateSource.REPAIR -> OwnRowDeviceInfoRepairFailed(reason)
        }

        private const val REASON = "reason"
        private const val CREDENTIAL = "credential"

        private fun dailyTag(
            pixelName: SyncPixelName,
            key: String,
            value: String,
        ): String = "${pixelName.pixelName}:$key:$value"
    }
}

internal fun Error.toAccountInfoKeyCreateFailureReason(): UnifiedDeviceListPixel.AccountInfoKeyCreateFailureReason =
    if (isHttpTooManyRequests()) {
        UnifiedDeviceListPixel.AccountInfoKeyCreateFailureReason.RATE_LIMITED
    } else {
        UnifiedDeviceListPixel.AccountInfoKeyCreateFailureReason.REQUEST_FAILED
    }

internal fun Error.toAccountInfoKeyWrapFailureReason(): UnifiedDeviceListPixel.AccountInfoKeyWrapFailureReason =
    if (isHttpTooManyRequests()) {
        UnifiedDeviceListPixel.AccountInfoKeyWrapFailureReason.RATE_LIMITED
    } else {
        UnifiedDeviceListPixel.AccountInfoKeyWrapFailureReason.REQUEST_FAILED
    }

internal fun Error.toAccountInfoKeyAdoptFailureReason(): UnifiedDeviceListPixel.AccountInfoKeyAdoptFailureReason =
    if (isHttpTooManyRequests()) {
        UnifiedDeviceListPixel.AccountInfoKeyAdoptFailureReason.RATE_LIMITED
    } else {
        UnifiedDeviceListPixel.AccountInfoKeyAdoptFailureReason.KEYS_FETCH_FAILED
    }

internal fun Error.toDeviceInfoWriteFailureReason(): UnifiedDeviceListPixel.DeviceInfoWriteFailureReason =
    if (isHttpTooManyRequests()) {
        UnifiedDeviceListPixel.DeviceInfoWriteFailureReason.RATE_LIMITED
    } else {
        UnifiedDeviceListPixel.DeviceInfoWriteFailureReason.REQUEST_FAILED
    }

private fun Error.isHttpTooManyRequests(): Boolean = code == API_CODE.TOO_MANY_REQUESTS_1.code

private fun DeviceInfoReadFailureReason.toPixelValue(): String = when (this) {
    DeviceInfoReadFailureReason.NOT_PUBLISHED_YET -> "not_published_yet"
    DeviceInfoReadFailureReason.BLOB_ABSENT -> "blob_absent"
    DeviceInfoReadFailureReason.BLOB_DECRYPT_FAILED -> "blob_decrypt_failed"
}

private fun DeviceCredential.toPixelValue(): String = when (this) {
    DeviceCredential.DDG -> "ddg"
    DeviceCredential.THIRD_PARTY -> "3party"
    DeviceCredential.NONE -> "none"
}

private fun AccountInfoKeyUnavailableReason.toPixelValue(): String = when (this) {
    AccountInfoKeyUnavailableReason.NO_KEY_ON_SERVER -> "no_key_on_server"
    AccountInfoKeyUnavailableReason.NO_WRAP_FOR_OUR_CREDENTIAL -> "no_wrap_for_our_credential"
    AccountInfoKeyUnavailableReason.UNWRAP_FAILED -> "unwrap_failed"
    AccountInfoKeyUnavailableReason.KEYS_FETCH_FAILED -> "keys_fetch_failed"
    AccountInfoKeyUnavailableReason.RATE_LIMITED -> "rate_limited"
}

private fun UnifiedDeviceListPixel.AccountInfoKeyCreateFailureReason.toPixelValue(): String = when (this) {
    UnifiedDeviceListPixel.AccountInfoKeyCreateFailureReason.MINT_FAILED -> "mint_failed"
    UnifiedDeviceListPixel.AccountInfoKeyCreateFailureReason.REQUEST_FAILED -> "request_failed"
    UnifiedDeviceListPixel.AccountInfoKeyCreateFailureReason.RATE_LIMITED -> "rate_limited"
}

private fun UnifiedDeviceListPixel.AccountInfoKeyWrapFailureReason.toPixelValue(): String = when (this) {
    UnifiedDeviceListPixel.AccountInfoKeyWrapFailureReason.UNWRAP_FAILED -> "unwrap_failed"
    UnifiedDeviceListPixel.AccountInfoKeyWrapFailureReason.REQUEST_FAILED -> "request_failed"
    UnifiedDeviceListPixel.AccountInfoKeyWrapFailureReason.RATE_LIMITED -> "rate_limited"
}

private fun UnifiedDeviceListPixel.AccountInfoKeyAdoptFailureReason.toPixelValue(): String = when (this) {
    UnifiedDeviceListPixel.AccountInfoKeyAdoptFailureReason.KEYS_FETCH_FAILED -> "keys_fetch_failed"
    UnifiedDeviceListPixel.AccountInfoKeyAdoptFailureReason.RATE_LIMITED -> "rate_limited"
}

private fun UnifiedDeviceListPixel.DeviceInfoWriteFailureReason.toPixelValue(): String = when (this) {
    UnifiedDeviceListPixel.DeviceInfoWriteFailureReason.ENCRYPT_FAILED -> "encrypt_failed"
    UnifiedDeviceListPixel.DeviceInfoWriteFailureReason.REQUEST_FAILED -> "request_failed"
    UnifiedDeviceListPixel.DeviceInfoWriteFailureReason.RATE_LIMITED -> "rate_limited"
}
