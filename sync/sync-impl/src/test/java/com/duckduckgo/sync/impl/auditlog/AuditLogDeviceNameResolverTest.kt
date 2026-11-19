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

import com.duckduckgo.sync.impl.AccountInfoKeyUnavailableReason
import com.duckduckgo.sync.impl.DecryptedDevice
import com.duckduckgo.sync.impl.DeviceFieldDecryptor
import com.duckduckgo.sync.impl.DeviceInfoDecryptor
import com.duckduckgo.sync.impl.DeviceInfoPayload
import com.duckduckgo.sync.impl.DeviceInfoSessionResult
import com.duckduckgo.sync.impl.DeviceV2
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.DEVICE_INFO_ADDED
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.LOGIN
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.LOGOUT
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Decoded
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Skipped
import com.duckduckgo.sync.impl.auditlog.DeviceNameSource.INFO
import com.duckduckgo.sync.impl.auditlog.DeviceNameSource.LATER_INFO
import com.duckduckgo.sync.impl.auditlog.DeviceNameSource.LEGACY
import com.duckduckgo.sync.impl.auditlog.DeviceNameSource.PLACEHOLDER
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class AuditLogDeviceNameResolverTest {

    private val session: DeviceInfoDecryptor.Session = mock()
    private val deviceInfoDecryptor: DeviceInfoDecryptor = mock()
    private val deviceFieldDecryptor: DeviceFieldDecryptor = mock()
    private val resolver = RealAuditLogDeviceNameResolver(deviceInfoDecryptor, deviceFieldDecryptor)

    @Before
    fun setup() {
        whenever(deviceInfoDecryptor.openSession()).thenReturn(DeviceInfoSessionResult.Available(session))
        whenever(session.decrypt(any())).thenReturn(Result.Error(reason = "undecryptable"))
        whenever(deviceFieldDecryptor.decrypt(any())).thenReturn(Result.Error(reason = "undecryptable"))
    }

    @Test
    fun whenDeviceInfoDecryptsThenNameFromInfo() {
        givenInfo("info-a", "Phone A", "phone")

        val result = resolver.resolve(listOf(entry("e1", LOGIN, device("a", info = "info-a"))))

        assertEquals(mapOf("e1" to ResolvedDeviceName("Phone A", "phone", INFO)), result)
    }

    @Test
    fun whenLoginHasNoInfoThenNameFromLaterDeviceInfoAdded() {
        givenInfo("info-a", "Phone A", "phone")

        val result = resolver.resolve(
            listOf(
                entry("e2", DEVICE_INFO_ADDED, device("a", info = "info-a")),
                entry("e1", LOGIN, device("a")),
            ),
        )

        assertEquals(mapOf("e1" to ResolvedDeviceName("Phone A", "phone", LATER_INFO)), result)
    }

    @Test
    fun whenSeveralDeviceInfoAddedBeforeNextLoginThenOldestUsed() {
        givenInfo("info-new", "Newer name", null)
        givenInfo("info-old", "Older name", null)

        val result = resolver.resolve(
            listOf(
                entry("e3", DEVICE_INFO_ADDED, device("a", info = "info-new")),
                entry("e2", DEVICE_INFO_ADDED, device("a", info = "info-old")),
                entry("e1", LOGIN, device("a")),
            ),
        )

        assertEquals(ResolvedDeviceName("Older name", null, LATER_INFO), result["e1"])
    }

    @Test
    fun whenOldestDeviceInfoAddedDoesNotDecryptThenNextOldestUsed() {
        givenInfo("info-new", "Newer name", null)

        val result = resolver.resolve(
            listOf(
                entry("e3", DEVICE_INFO_ADDED, device("a", info = "info-new")),
                entry("e2", DEVICE_INFO_ADDED, device("a", info = "info-broken")),
                entry("e1", LOGIN, device("a")),
            ),
        )

        assertEquals(ResolvedDeviceName("Newer name", null, LATER_INFO), result["e1"])
    }

    @Test
    fun whenDeviceInfoAddedIsAfterTheDevicesNextLoginThenNotUsedForEarlierLogin() {
        givenInfo("info-a", "Phone A", null)

        val result = resolver.resolve(
            listOf(
                entry("e3", DEVICE_INFO_ADDED, device("a", info = "info-a")),
                entry("e2", LOGIN, device("a")),
                entry("e1", LOGIN, device("a")),
            ),
        )

        assertEquals(LATER_INFO, result["e2"]?.source)
        assertEquals(PLACEHOLDER, result["e1"]?.source)
    }

    @Test
    fun whenDeviceInfoAddedIsForAnotherDeviceThenNotUsed() {
        givenInfo("info-b", "Phone B", null)

        val result = resolver.resolve(
            listOf(
                entry("e2", DEVICE_INFO_ADDED, device("b", info = "info-b")),
                entry("e1", LOGIN, device("a")),
            ),
        )

        assertEquals(PLACEHOLDER, result["e1"]?.source)
    }

    @Test
    fun whenDeviceInfoAddedIsOlderThanLoginThenNotUsed() {
        givenInfo("info-a", "Phone A", null)

        val result = resolver.resolve(
            listOf(
                entry("e2", LOGIN, device("a")),
                entry("e1", DEVICE_INFO_ADDED, device("a", info = "info-a")),
            ),
        )

        assertEquals(PLACEHOLDER, result["e2"]?.source)
    }

    @Test
    fun whenNoInfoThenLegacyNameUsed() {
        val legacyDevice = device("a")
        whenever(deviceFieldDecryptor.decrypt(legacyDevice)).thenReturn(Result.Success(DecryptedDevice("a", "Legacy A", "desktop")))

        val result = resolver.resolve(listOf(entry("e1", LOGIN, legacyDevice)))

        assertEquals(mapOf("e1" to ResolvedDeviceName("Legacy A", "desktop", LEGACY)), result)
    }

    @Test
    fun whenInfoDoesNotDecryptThenLegacyNameUsed() {
        val legacyDevice = device("a", info = "info-broken")
        whenever(deviceFieldDecryptor.decrypt(legacyDevice)).thenReturn(Result.Success(DecryptedDevice("a", "Legacy A", null)))

        assertEquals(LEGACY, resolver.resolve(listOf(entry("e1", LOGIN, legacyDevice)))["e1"]?.source)
    }

    @Test
    fun whenDdgLegacyNameDoesNotDecryptThenPlaceholderWithNoType() {
        val result = resolver.resolve(listOf(entry("e1", LOGIN, device("a", credentialId = "ddg"))))

        assertEquals(mapOf("e1" to ResolvedDeviceName("Unknown device", null, PLACEHOLDER)), result)
    }

    @Test
    fun whenThirdPartyLegacyNameDoesNotDecryptThenPlaceholderWithBrowserType() {
        val result = resolver.resolve(listOf(entry("e1", LOGIN, device("a", credentialId = "3party"))))

        assertEquals(mapOf("e1" to ResolvedDeviceName("Unknown device", "Browser", PLACEHOLDER)), result)
    }

    @Test
    fun whenLogoutThenRemovedDeviceNameResolvedWithoutLaterInfo() {
        givenInfo("info-a", "Phone A", null)

        val result = resolver.resolve(
            listOf(
                entry("e2", DEVICE_INFO_ADDED, device("a", info = "info-a")),
                entry("e1", LOGOUT, device("a"), actor = device("b")),
            ),
        )

        assertEquals(mapOf("e1" to ResolvedDeviceName("Unknown device", null, PLACEHOLDER)), result)
    }

    @Test
    fun whenDeviceInfoAddedOrSkippedEntriesThenNoNameReturnedForThem() {
        givenInfo("info-a", "Phone A", null)

        val result = resolver.resolve(
            listOf(
                entry("e2", DEVICE_INFO_ADDED, device("a", info = "info-a")),
                Skipped("e1", AuditLogEntrySkipReason.UNKNOWN_TYPE),
            ),
        )

        assertEquals(emptyMap<String, ResolvedDeviceName>(), result)
    }

    @Test
    fun whenKeyUnavailableThenLegacyUsedWithoutDecryptingInfo() {
        whenever(deviceInfoDecryptor.openSession())
            .thenReturn(DeviceInfoSessionResult.Unavailable(AccountInfoKeyUnavailableReason.KEYS_FETCH_FAILED))
        val legacyDevice = device("a", info = "info-a")
        whenever(deviceFieldDecryptor.decrypt(legacyDevice)).thenReturn(Result.Success(DecryptedDevice("a", "Legacy A", null)))

        val result = resolver.resolve(listOf(entry("e1", LOGIN, legacyDevice)))

        assertEquals(LEGACY, result["e1"]?.source)
        verify(session, never()).decrypt(any())
    }

    @Test
    fun whenManyEntriesThenOneSessionOpened() {
        resolver.resolve(listOf(entry("e2", LOGIN, device("b", info = "info-b")), entry("e1", LOGIN, device("a", info = "info-a"))))

        verify(deviceInfoDecryptor, times(1)).openSession()
    }

    @Test
    fun whenSameInfoOnManyRowsThenDecryptedOnce() {
        givenInfo("info-a", "Phone A", null)

        val result = resolver.resolve(
            listOf(
                entry("e3", DEVICE_INFO_ADDED, device("a", info = "info-a")),
                entry("e2", LOGOUT, device("a", info = "info-a"), actor = device("b")),
                entry("e1", LOGIN, device("a")),
            ),
        )

        assertEquals(setOf("Phone A"), result.values.map { it.name }.toSet())
        verify(session, times(1)).decrypt("info-a")
    }

    @Test
    fun whenNoRowsToNameThenNoSessionOpened() {
        resolver.resolve(listOf(entry("e1", DEVICE_INFO_ADDED, device("a", info = "info-a"))))

        verify(deviceInfoDecryptor, never()).openSession()
    }

    private fun givenInfo(jwe: String, name: String, type: String?) {
        whenever(session.decrypt(jwe)).thenReturn(Result.Success(DeviceInfoPayload(name = name, type = type)))
    }

    private fun device(id: String, info: String? = null, credentialId: String? = "ddg") =
        DeviceV2(deviceId = id, deviceName = "enc-name-$id", deviceInfo = info, credentialId = credentialId)

    private fun entry(id: String, type: AuditLogEntryType, device: DeviceV2, actor: DeviceV2? = null) =
        Decoded(id = id, type = type, occurredAt = Instant.EPOCH, device = device, actor = actor)
}
