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

package com.duckduckgo.sync.internal.ui

import com.duckduckgo.sync.impl.AccountInfoKeyUnavailableReason
import com.duckduckgo.sync.impl.AuditLogEntry
import com.duckduckgo.sync.impl.AuditLogPage
import com.duckduckgo.sync.impl.DeviceV2
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.impl.auditlog.AuditLogDecodeResult
import com.duckduckgo.sync.impl.auditlog.AuditLogDeviceNameResolver
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryDecoder
import com.duckduckgo.sync.impl.auditlog.AuditLogEntrySkipReason
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType
import com.duckduckgo.sync.impl.auditlog.AuditLogResult
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Decoded
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Skipped
import com.duckduckgo.sync.impl.auditlog.DeviceNameSource
import com.duckduckgo.sync.impl.auditlog.ResolvedDeviceName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class AuditLogDebugReportTest {

    private val syncApi: SyncApi = mock()
    private val decoder: AuditLogEntryDecoder = mock()
    private val resolver: AuditLogDeviceNameResolver = mock()

    private val report = AuditLogDebugReport(syncApi, decoder, resolver)

    @Test
    fun whenFetchFailsThenShowsErrorAndDoesNotDecode() {
        whenever(syncApi.getAuditLogLatest(TOKEN)).thenReturn(AuditLogResult.RateLimited(418))

        val text = report.fetchAndDescribe(TOKEN, OWN_DEVICE_ID)

        assertEquals("Error: RateLimited(statusCode=418)", text)
        verify(decoder, never()).decode(any())
    }

    @Test
    fun whenPageIsEmptyThenSaysSo() {
        givenPage(emptyList(), before = null)

        val text = report.fetchAndDescribe(TOKEN, OWN_DEVICE_ID)

        assertTrue(text, text.contains("0 entries"))
        assertTrue(text, text.contains("Older entries: no"))
    }

    @Test
    fun whenOlderEntriesExistThenSaysSo() {
        givenPage(emptyList(), before = "older")

        val text = report.fetchAndDescribe(TOKEN, OWN_DEVICE_ID)

        assertTrue(text, text.contains("Older entries: yes"))
    }

    @Test
    fun whenLoginIsDecodedThenShowsTypeTimeNameAndNameSource() {
        val login = decoded("e1", AuditLogEntryType.LOGIN, device = DeviceV2(deviceId = "other", credentialId = "3party"))
        givenPage(listOf(login), names = mapOf("e1" to ResolvedDeviceName("Work laptop", "desktop", DeviceNameSource.LATER_INFO)))

        val text = report.fetchAndDescribe(TOKEN, OWN_DEVICE_ID)

        assertTrue(text, text.contains("LOGIN  $OCCURRED_AT"))
        assertTrue(text, text.contains("name: Work laptop (desktop) [LATER_INFO]"))
        assertTrue(text, text.contains("device: other [credential=3party]"))
    }

    @Test
    fun whenEntryIsForThisDeviceThenMarksIt() {
        val login = decoded("e1", AuditLogEntryType.LOGIN, device = DeviceV2(deviceId = OWN_DEVICE_ID))
        givenPage(listOf(login), names = mapOf("e1" to ResolvedDeviceName("Phone", null, DeviceNameSource.INFO)))

        val text = report.fetchAndDescribe(TOKEN, OWN_DEVICE_ID)

        assertTrue(text, text.contains("name: Phone [INFO]"))
        assertTrue(text, text.contains("device: $OWN_DEVICE_ID (this device) [credential=ddg]"))
    }

    @Test
    fun whenLogoutIsDecodedThenShowsActor() {
        val logout = decoded(
            "e1",
            AuditLogEntryType.LOGOUT,
            device = DeviceV2(deviceId = "removed"),
            actor = DeviceV2(deviceId = OWN_DEVICE_ID),
        )
        givenPage(listOf(logout), names = mapOf("e1" to ResolvedDeviceName("Unknown device", null, DeviceNameSource.PLACEHOLDER)))

        val text = report.fetchAndDescribe(TOKEN, OWN_DEVICE_ID)

        assertTrue(text, text.contains("LOGOUT  $OCCURRED_AT"))
        assertTrue(text, text.contains("name: Unknown device [PLACEHOLDER]"))
        assertTrue(text, text.contains("actor: $OWN_DEVICE_ID (this device)"))
    }

    @Test
    fun whenDeviceInfoAddedIsDecodedThenShowsItWithoutName() {
        val infoAdded = decoded("e1", AuditLogEntryType.DEVICE_INFO_ADDED, device = DeviceV2(deviceId = "other"))
        givenPage(listOf(infoAdded))

        val text = report.fetchAndDescribe(TOKEN, OWN_DEVICE_ID)

        assertTrue(text, text.contains("DEVICE_INFO_ADDED  $OCCURRED_AT"))
        assertTrue(text, !text.contains("name:"))
    }

    @Test
    fun whenEntryIsSkippedThenShowsReason() {
        givenPage(listOf(Skipped("e1", AuditLogEntrySkipReason.PAYLOAD_DECRYPT_ERROR)))

        val text = report.fetchAndDescribe(TOKEN, OWN_DEVICE_ID)

        assertTrue(text, text.contains("SKIPPED  PAYLOAD_DECRYPT_ERROR"))
        assertTrue(text, text.contains("entry: e1"))
        assertTrue(text, text.contains("1 entries: 0 decoded, 1 skipped"))
    }

    @Test
    fun whenKeyIsUnavailableThenShowsReason() {
        givenPage(
            listOf(Skipped("e1", AuditLogEntrySkipReason.KEY_NOT_FOUND)),
            keyUnavailableReason = AccountInfoKeyUnavailableReason.RATE_LIMITED,
        )

        val text = report.fetchAndDescribe(TOKEN, OWN_DEVICE_ID)

        assertTrue(text, text.contains("account_info key unavailable: RATE_LIMITED"))
    }

    @Test
    fun whenSeveralEntriesThenKeepsServerOrderNewestFirst() {
        val newer = decoded("newer", AuditLogEntryType.LOGIN, device = DeviceV2(deviceId = "a"))
        val older = decoded("older", AuditLogEntryType.LOGIN, device = DeviceV2(deviceId = "b"))
        givenPage(
            listOf(newer, older),
            names = mapOf(
                "newer" to ResolvedDeviceName("Newer", null, DeviceNameSource.INFO),
                "older" to ResolvedDeviceName("Older", null, DeviceNameSource.LEGACY),
            ),
        )

        val text = report.fetchAndDescribe(TOKEN, OWN_DEVICE_ID)

        assertTrue(text, text.indexOf("name: Newer") < text.indexOf("name: Older"))
    }

    private fun givenPage(
        decodedEntries: List<DecodedAuditLogEntry>,
        names: Map<String, ResolvedDeviceName> = emptyMap(),
        keyUnavailableReason: AccountInfoKeyUnavailableReason? = null,
        before: String? = null,
    ) {
        val rawEntries = decodedEntries.map { AuditLogEntry(id = it.id, rawVersion = "1", payload = "jwe") }
        whenever(syncApi.getAuditLogLatest(TOKEN)).thenReturn(
            AuditLogResult.Success(AuditLogPage(entries = rawEntries, after = "after", before = before)),
        )
        whenever(decoder.decode(rawEntries)).thenReturn(AuditLogDecodeResult(decodedEntries, keyUnavailableReason))
        whenever(resolver.resolve(decodedEntries)).thenReturn(names)
    }

    private fun decoded(
        id: String,
        type: AuditLogEntryType,
        device: DeviceV2,
        actor: DeviceV2? = null,
    ) = Decoded(id, type, Instant.parse(OCCURRED_AT), device, actor)

    private companion object {
        const val TOKEN = "token"
        const val OWN_DEVICE_ID = "own"
        const val OCCURRED_AT = "2026-11-10T10:00:00Z"
    }
}
