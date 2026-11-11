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

import com.duckduckgo.sync.impl.DeviceFieldDecryptor
import com.duckduckgo.sync.impl.DeviceInfoDecryptor
import com.duckduckgo.sync.impl.DeviceInfoPayload
import com.duckduckgo.sync.impl.DeviceInfoSessionResult
import com.duckduckgo.sync.impl.DeviceV2
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.ThirdPartyDeviceListDecryptor.Companion.FALLBACK_NAME
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.DEVICE_INFO_ADDED
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.LOGIN
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.LOGOUT
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Decoded
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Skipped
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class RealPendingLoginsUpdaterTest {

    private val deviceFieldDecryptor: DeviceFieldDecryptor = mock()
    private val deviceInfoDecryptor = FakeDeviceInfoDecryptor()

    private val testee = RealPendingLoginsUpdater(
        RealAuditLogDeviceNameResolver(deviceInfoDecryptor, deviceFieldDecryptor),
        deviceInfoDecryptor,
    )

    @Before
    fun setup() {
        whenever(deviceFieldDecryptor.decrypt(any())).thenReturn(Result.Error(reason = "no legacy name"))
    }

    @Test
    fun whenOwnDeviceLogsInThenItIsNotAdded() {
        val update = testee.prepare(listOf(login("e1", OWN_DEVICE, info = "info:Me")), OWN_DEVICE, emptyList())

        assertEquals(emptyList<PendingLogin>(), update.newLogins)
    }

    @Test
    fun whenEntriesHaveLogoutsDeviceInfoAddedAndSkippedThenOnlyLoginsAreAdded() {
        val entries = listOf(
            Skipped("e4", AuditLogEntrySkipReason.UNKNOWN_TYPE),
            logout("e3", OTHER_DEVICE),
            infoAdded("e2", THIRD_DEVICE, info = "info:Tablet"),
            login("e1", OTHER_DEVICE, info = "info:Laptop", at = 1),
        )

        val update = testee.prepare(entries, OWN_DEVICE, emptyList())

        assertEquals(listOf(pending("e1", OTHER_DEVICE, "Laptop", 1)), update.newLogins)
    }

    @Test
    fun whenLoginHasNoInfoThenNewerDeviceInfoAddedNamesItAndOrderIsOldestFirst() {
        val entries = listOf(
            infoAdded("e3", OTHER_DEVICE, info = "info:Laptop", at = 3),
            login("e2", THIRD_DEVICE, at = 2),
            login("e1", OTHER_DEVICE, at = 1),
        )

        val update = testee.prepare(entries, OWN_DEVICE, emptyList())

        assertEquals(listOf(pending("e1", OTHER_DEVICE, "Laptop", 1), pending("e2", THIRD_DEVICE, FALLBACK_NAME, 2)), update.newLogins)
    }

    @Test
    fun whenSameLoginAppearsTwiceThenItIsAddedOnce() {
        val entries = listOf(login("e1", OTHER_DEVICE, info = "info:Laptop", at = 1), login("e1", OTHER_DEVICE, info = "info:Laptop", at = 1))

        val update = testee.prepare(entries, OWN_DEVICE, emptyList())

        assertEquals(listOf(pending("e1", OTHER_DEVICE, "Laptop", 1)), update.newLogins)
    }

    @Test
    fun whenDeviceInfoAddedIsForPendingDeviceThenItGivesLateName() {
        val update = testee.prepare(listOf(infoAdded("e2", OTHER_DEVICE, info = "info:Laptop")), OWN_DEVICE, listOf(fallback("e1", OTHER_DEVICE)))

        assertEquals(mapOf(OTHER_DEVICE to "Laptop"), update.lateNames)
    }

    @Test
    fun whenSeveralDeviceInfoAddedForPendingDeviceThenOldestThatDecryptsIsUsed() {
        val entries = listOf(
            infoAdded("e4", OTHER_DEVICE, info = "info:Newer"),
            infoAdded("e3", OTHER_DEVICE, info = "info:Older"),
            infoAdded("e2", OTHER_DEVICE, info = "undecryptable"),
        )

        val update = testee.prepare(entries, OWN_DEVICE, listOf(fallback("e1", OTHER_DEVICE)))

        assertEquals(mapOf(OTHER_DEVICE to "Older"), update.lateNames)
    }

    @Test
    fun whenDeviceInfoAddedFollowsLoginOfSameDeviceThenItGivesNoLateName() {
        val entries = listOf(infoAdded("e3", OTHER_DEVICE, info = "info:Laptop", at = 3), login("e2", OTHER_DEVICE, at = 2))

        val update = testee.prepare(entries, OWN_DEVICE, listOf(fallback("e1", OTHER_DEVICE)))

        assertEquals(emptyMap<String, String>(), update.lateNames)
        assertEquals(listOf(pending("e2", OTHER_DEVICE, "Laptop", 2)), update.newLogins)
    }

    @Test
    fun whenDeviceInfoAddedHasNoPendingRowThenNothingIsDecrypted() {
        val update = testee.prepare(listOf(infoAdded("e1", OTHER_DEVICE, info = "info:Laptop")), OWN_DEVICE, emptyList())

        assertEquals(emptyMap<String, String>(), update.lateNames)
        assertEquals(0, deviceInfoDecryptor.decryptCount)
    }

    @Test
    fun whenAppliedThenLateNameGoesOnlyToNewestRowOfThatDevice() {
        val current = listOf(fallback("e1", OTHER_DEVICE, at = 1), fallback("e2", OTHER_DEVICE, at = 2), fallback("e3", THIRD_DEVICE, at = 3))

        val applied = PendingLoginsUpdate(newLogins = emptyList(), lateNames = mapOf(OTHER_DEVICE to "Laptop")).applyTo(current)

        assertEquals(
            listOf(fallback("e1", OTHER_DEVICE, at = 1), pending("e2", OTHER_DEVICE, "Laptop", 2), fallback("e3", THIRD_DEVICE, at = 3)),
            applied.pendingLogins,
        )
        assertEquals(0, applied.added)
        assertEquals(1, applied.renamed)
    }

    @Test
    fun whenAppliedThenLoginsAlreadyInListAreNotAddedAgain() {
        val current = listOf(pending("e1", OTHER_DEVICE, "Laptop", 1))
        val newLogins = listOf(pending("e1", OTHER_DEVICE, "Laptop", 1), pending("e2", THIRD_DEVICE, "Tablet", 2))

        val applied = PendingLoginsUpdate(newLogins, lateNames = emptyMap()).applyTo(current)

        assertEquals(listOf(pending("e1", OTHER_DEVICE, "Laptop", 1), pending("e2", THIRD_DEVICE, "Tablet", 2)), applied.pendingLogins)
        assertEquals(1, applied.added)
        assertEquals(0, applied.renamed)
    }

    private fun login(id: String, deviceId: String, info: String? = null, at: Long = 0) =
        Decoded(id, LOGIN, Instant.ofEpochSecond(at), DeviceV2(deviceId = deviceId, deviceInfo = info), null)

    private fun logout(id: String, deviceId: String) =
        Decoded(id, LOGOUT, Instant.EPOCH, DeviceV2(deviceId = deviceId, deviceInfo = "info:Removed"), DeviceV2(deviceId = OWN_DEVICE))

    private fun infoAdded(id: String, deviceId: String, info: String, at: Long = 0) =
        Decoded(id, DEVICE_INFO_ADDED, Instant.ofEpochSecond(at), DeviceV2(deviceId = deviceId, deviceInfo = info), null)

    private fun pending(id: String, deviceId: String, name: String, at: Long) = PendingLogin(id, deviceId, name, Instant.ofEpochSecond(at))

    private fun fallback(id: String, deviceId: String, at: Long = 0) = pending(id, deviceId, FALLBACK_NAME, at)

    /** `info:<name>` decrypts to `<name>`; anything else fails. */
    private class FakeDeviceInfoDecryptor : DeviceInfoDecryptor {
        var decryptCount = 0

        override fun openSession(): DeviceInfoSessionResult = DeviceInfoSessionResult.Available(
            object : DeviceInfoDecryptor.Session {
                override fun decrypt(deviceInfoJwe: String): Result<DeviceInfoPayload> {
                    decryptCount++
                    return if (deviceInfoJwe.startsWith("info:")) {
                        Result.Success(DeviceInfoPayload(name = deviceInfoJwe.removePrefix("info:")))
                    } else {
                        Result.Error(reason = "not info")
                    }
                }
            },
        )
    }

    private companion object {
        const val OWN_DEVICE = "own-device"
        const val OTHER_DEVICE = "other-device"
        const val THIRD_DEVICE = "third-device"
    }
}
