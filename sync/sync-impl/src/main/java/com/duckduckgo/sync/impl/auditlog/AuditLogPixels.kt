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
import java.time.Duration
import javax.inject.Inject

/** The daily pixels keep only the first fire of the day, so for [fireUndecryptable] the first reason of the day is the one sent. */
interface AuditLogPixels {
    fun fireDeviceActivityScreenShown()

    /** [statusCode] is null when there was no HTTP response. */
    fun fireFetchError(statusCode: Int?)

    fun fireUnknownVersion()

    fun fireUndecryptable(reason: AuditLogUndecryptableReason)

    /** [delay] is sent only as a bucket. */
    fun fireNotificationShown(delay: Duration)

    fun fireNotificationDisabled()

    fun fireNotificationTapped()

    /** [canNotify] is false when the user turned new login notifications off or the system does not let the app post them. */
    fun fireNotificationPermission(canNotify: Boolean)
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

    override fun fireNotificationShown(delay: Duration) {
        pixel.fire(
            SyncPixelName.SYNC_DEVICE_LOGIN_NOTIFICATION_SHOWN,
            parameters = mapOf(DELAY_BUCKET to delayBucket(delay)),
            type = PixelType.Count,
        )
    }

    override fun fireNotificationDisabled() {
        pixel.fire(SyncPixelName.SYNC_DEVICE_LOGIN_NOTIFICATION_DISABLED, type = PixelType.Daily())
    }

    override fun fireNotificationTapped() {
        pixel.fire(SyncPixelName.SYNC_DEVICE_LOGIN_NOTIFICATION_TAPPED, type = PixelType.Count)
    }

    override fun fireNotificationPermission(canNotify: Boolean) {
        pixel.fire(
            SyncPixelName.SYNC_DEVICE_LOGIN_NOTIFICATION_PERMISSION,
            parameters = mapOf(STATE to if (canNotify) "enabled" else "disabled"),
            type = PixelType.Daily(),
        )
    }

    // Each bucket includes its lower bound and excludes its upper one. A negative delay comes from a wrong device clock.
    private fun delayBucket(delay: Duration): String = when {
        delay.isNegative -> "invalid"
        delay < Duration.ofMinutes(1) -> "0-1m"
        delay < Duration.ofMinutes(5) -> "1-5m"
        delay < Duration.ofMinutes(30) -> "5-30m"
        delay < Duration.ofHours(2) -> "30m-2h"
        delay < Duration.ofHours(24) -> "2-24h"
        else -> "24h+"
    }

    private companion object {
        const val STATUS_CODE = "status_code"
        const val REASON = "reason"
        const val DELAY_BUCKET = "delay_bucket"
        const val STATE = "state"
    }
}
