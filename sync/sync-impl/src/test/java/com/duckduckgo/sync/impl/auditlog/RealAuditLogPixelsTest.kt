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
import com.duckduckgo.sync.impl.pixels.SyncPixelName
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Duration

class RealAuditLogPixelsTest {

    private val pixel: Pixel = mock()
    private val pixels = RealAuditLogPixels(pixel)

    @Test
    fun whenDeviceActivityScreenShownThenCountPixelFired() {
        pixels.fireDeviceActivityScreenShown()

        verify(pixel).fire(SyncPixelName.SYNC_DEVICE_ACTIVITY_SCREEN_SHOWN, emptyMap(), emptyMap(), PixelType.Count)
    }

    @Test
    fun whenFetchErrorWithStatusCodeThenDailyPixelFiredWithStatusCode() {
        pixels.fireFetchError(418)

        verify(pixel).fire(SyncPixelName.SYNC_AUDIT_LOG_FETCH_ERROR, mapOf("status_code" to "418"), emptyMap(), PixelType.Daily())
    }

    @Test
    fun whenFetchErrorWithoutStatusCodeThenDailyPixelFiredWithoutParameters() {
        pixels.fireFetchError(null)

        verify(pixel).fire(SyncPixelName.SYNC_AUDIT_LOG_FETCH_ERROR, emptyMap(), emptyMap(), PixelType.Daily())
    }

    @Test
    fun whenUnknownVersionThenDailyPixelFired() {
        pixels.fireUnknownVersion()

        verify(pixel).fire(SyncPixelName.SYNC_AUDIT_LOG_UNKNOWN_VERSION, emptyMap(), emptyMap(), PixelType.Daily())
    }

    @Test
    fun whenUndecryptableKeyNotFoundThenReasonSent() {
        assertUndecryptableSends(AuditLogUndecryptableReason.KEY_NOT_FOUND, "key_not_found")
    }

    @Test
    fun whenUndecryptablePayloadThenReasonSent() {
        assertUndecryptableSends(AuditLogUndecryptableReason.PAYLOAD_DECRYPT_ERROR, "payload_decrypt_error")
    }

    @Test
    fun whenUndecryptableDeviceInfoThenReasonSent() {
        assertUndecryptableSends(AuditLogUndecryptableReason.DEVICE_INFO_DECRYPT_ERROR, "device_info_decrypt_error")
    }

    @Test
    fun whenUndecryptableLegacyNameTypeThenReasonSent() {
        assertUndecryptableSends(AuditLogUndecryptableReason.LEGACY_NAME_TYPE_DECRYPT_ERROR, "legacy_name_type_decrypt_error")
    }

    @Test
    fun whenNotificationShownWithNoDelayThenBucketIsUnderOneMinute() = assertShownBucket(Duration.ZERO, "0-1m")

    @Test
    fun whenNotificationShownJustUnderOneMinuteThenBucketIsUnderOneMinute() = assertShownBucket(justUnder(Duration.ofMinutes(1)), "0-1m")

    @Test
    fun whenNotificationShownAtOneMinuteThenBucketIsOneToFiveMinutes() = assertShownBucket(Duration.ofMinutes(1), "1-5m")

    @Test
    fun whenNotificationShownJustUnderFiveMinutesThenBucketIsOneToFiveMinutes() = assertShownBucket(justUnder(Duration.ofMinutes(5)), "1-5m")

    @Test
    fun whenNotificationShownAtFiveMinutesThenBucketIsFiveToThirtyMinutes() = assertShownBucket(Duration.ofMinutes(5), "5-30m")

    @Test
    fun whenNotificationShownJustUnderThirtyMinutesThenBucketIsFiveToThirtyMinutes() =
        assertShownBucket(justUnder(Duration.ofMinutes(30)), "5-30m")

    @Test
    fun whenNotificationShownAtThirtyMinutesThenBucketIsThirtyMinutesToTwoHours() = assertShownBucket(Duration.ofMinutes(30), "30m-2h")

    @Test
    fun whenNotificationShownJustUnderTwoHoursThenBucketIsThirtyMinutesToTwoHours() = assertShownBucket(justUnder(Duration.ofHours(2)), "30m-2h")

    @Test
    fun whenNotificationShownAtTwoHoursThenBucketIsTwoToTwentyFourHours() = assertShownBucket(Duration.ofHours(2), "2-24h")

    @Test
    fun whenNotificationShownJustUnderOneDayThenBucketIsTwoToTwentyFourHours() = assertShownBucket(justUnder(Duration.ofHours(24)), "2-24h")

    @Test
    fun whenNotificationShownAtOneDayThenBucketIsOverOneDay() = assertShownBucket(Duration.ofHours(24), "24h+")

    @Test
    fun whenNotificationShownWithNegativeDelayThenBucketIsInvalid() = assertShownBucket(Duration.ofMillis(-1), "invalid")

    @Test
    fun whenNotificationDisabledThenDailyPixelFired() {
        pixels.fireNotificationDisabled()

        verify(pixel).fire(SyncPixelName.SYNC_DEVICE_LOGIN_NOTIFICATION_DISABLED, emptyMap(), emptyMap(), PixelType.Daily())
    }

    @Test
    fun whenNotificationTappedThenCountPixelFired() {
        pixels.fireNotificationTapped()

        verify(pixel).fire(SyncPixelName.SYNC_DEVICE_LOGIN_NOTIFICATION_TAPPED, emptyMap(), emptyMap(), PixelType.Count)
    }

    @Test
    fun whenNotificationPermissionEnabledThenDailyPixelFiredWithEnabledState() {
        pixels.fireNotificationPermission(canNotify = true)

        verify(pixel).fire(SyncPixelName.SYNC_DEVICE_LOGIN_NOTIFICATION_PERMISSION, mapOf("state" to "enabled"), emptyMap(), PixelType.Daily())
    }

    @Test
    fun whenNotificationPermissionDisabledThenDailyPixelFiredWithDisabledState() {
        pixels.fireNotificationPermission(canNotify = false)

        verify(pixel).fire(SyncPixelName.SYNC_DEVICE_LOGIN_NOTIFICATION_PERMISSION, mapOf("state" to "disabled"), emptyMap(), PixelType.Daily())
    }

    private fun assertShownBucket(delay: Duration, bucket: String) {
        pixels.fireNotificationShown(delay)

        verify(pixel).fire(SyncPixelName.SYNC_DEVICE_LOGIN_NOTIFICATION_SHOWN, mapOf("delay_bucket" to bucket), emptyMap(), PixelType.Count)
    }

    private fun justUnder(duration: Duration) = duration.minusMillis(1)

    private fun assertUndecryptableSends(reason: AuditLogUndecryptableReason, value: String) {
        pixels.fireUndecryptable(reason)

        verify(pixel).fire(SyncPixelName.SYNC_AUDIT_LOG_UNDECRYPTABLE, mapOf("reason" to value), emptyMap(), PixelType.Daily())
    }
}
