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

package com.duckduckgo.sync.impl.auditlog

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

/** The daily pixels keep only the first fire of the day, so for [fireUndecryptable] the first reason of the day is the one sent. */
interface AuditLogPixels {
    fun fireDeviceActivityScreenShown()

    /** [statusCode] is null when there was no HTTP response. */
    fun fireFetchError(statusCode: Int?)

    fun fireUnknownVersion()

    fun fireUndecryptable(reason: AuditLogUndecryptableReason)
}

fun AuditLogPixels.fireFetchErrorIfFailed(result: AuditLogResult) {
    val statusCode = when (result) {
        is AuditLogResult.Success -> return
        is AuditLogResult.RateLimited -> result.statusCode
        is AuditLogResult.InvalidCursor -> HTTP_BAD_REQUEST
        is AuditLogResult.AuthError -> result.statusCode
        is AuditLogResult.ServerError -> result.statusCode
        is AuditLogResult.NetworkError -> null
    }
    fireFetchError(statusCode)
}

private const val HTTP_BAD_REQUEST = 400

enum class AuditLogUndecryptableReason(val pixelValue: String) {
    KEY_NOT_FOUND("key_not_found"),
    PAYLOAD_DECRYPT_ERROR("payload_decrypt_error"),
    DEVICE_INFO_DECRYPT_ERROR("device_info_decrypt_error"),
    LEGACY_NAME_TYPE_DECRYPT_ERROR("legacy_name_type_decrypt_error"),
}

@ContributesBinding(AppScope::class)
class RealAuditLogPixels @Inject constructor(
    private val pixel: Pixel,
) : AuditLogPixels {

    override fun fireDeviceActivityScreenShown() {
        pixel.fire(SyncPixelName.SYNC_DEVICE_ACTIVITY_SCREEN_SHOWN, type = PixelType.Count)
    }

    override fun fireFetchError(statusCode: Int?) {
        pixel.fire(
            SyncPixelName.SYNC_AUDIT_LOG_FETCH_ERROR,
            parameters = statusCode?.let { mapOf(STATUS_CODE to it.toString()) }.orEmpty(),
            type = PixelType.Daily(),
        )
    }

    override fun fireUnknownVersion() {
        pixel.fire(SyncPixelName.SYNC_AUDIT_LOG_UNKNOWN_VERSION, type = PixelType.Daily())
    }

    override fun fireUndecryptable(reason: AuditLogUndecryptableReason) {
        pixel.fire(
            SyncPixelName.SYNC_AUDIT_LOG_UNDECRYPTABLE,
            parameters = mapOf(REASON to reason.pixelValue),
            type = PixelType.Daily(),
        )
    }

    private companion object {
        const val STATUS_CODE = "status_code"
        const val REASON = "reason"
    }
}
