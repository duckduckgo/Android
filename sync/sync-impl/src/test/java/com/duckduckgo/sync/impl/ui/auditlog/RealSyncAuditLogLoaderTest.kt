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

package com.duckduckgo.sync.impl.ui.auditlog

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.AccountInfoKeyUnavailableReason
import com.duckduckgo.sync.impl.AuditLogEntry
import com.duckduckgo.sync.impl.AuditLogPage
import com.duckduckgo.sync.impl.DeviceType
import com.duckduckgo.sync.impl.DeviceV2
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.impl.auditlog.AuditLogDecodeResult
import com.duckduckgo.sync.impl.auditlog.AuditLogDeviceNameResolver
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryDecoder
import com.duckduckgo.sync.impl.auditlog.AuditLogEntrySkipReason
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.DEVICE_INFO_ADDED
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.LOGIN
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.LOGOUT
import com.duckduckgo.sync.impl.auditlog.AuditLogResult
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Decoded
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Skipped
import com.duckduckgo.sync.impl.auditlog.DeviceNameSource
import com.duckduckgo.sync.impl.auditlog.ResolvedDeviceName
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogAction.REMOVED
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogAction.SYNCED
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Instant

class RealSyncAuditLogLoaderTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val syncStore: SyncStore = mock()
    private val syncApi: SyncApi = mock()
    private val decoder: AuditLogEntryDecoder = mock()
    private val resolver: AuditLogDeviceNameResolver = mock()

    private val testee = RealSyncAuditLogLoader(
        syncStore = syncStore,
        syncApi = syncApi,
        entryDecoder = decoder,
        nameResolver = resolver,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenNoTokenThenFailsWithoutCallingServer() = runTest {
        whenever(syncStore.token).thenReturn(null)

        assertEquals(SyncAuditLogLoadResult.Failed, testee.loadLatest())
        verifyNoInteractions(syncApi)
    }

    @Test
    fun whenServerFailsThenFails() = runTest {
        givenToken()
        whenever(syncApi.getAuditLogLatest(TOKEN)).thenReturn(AuditLogResult.ServerError(500))

        assertEquals(SyncAuditLogLoadResult.Failed, testee.loadLatest())
    }

    @Test
    fun whenALayerBelowThrowsThenFails() = runTest {
        whenever(syncStore.token).thenThrow(IllegalStateException("keystore"))

        assertEquals(SyncAuditLogLoadResult.Failed, testee.loadLatest())
    }

    @Test
    fun whenAccountInfoKeyIsUnavailableThenFails() = runTest {
        givenPage(
            decoded = listOf(Skipped("e1", AuditLogEntrySkipReason.KEY_NOT_FOUND)),
            keyUnavailableReason = AccountInfoKeyUnavailableReason.KEYS_FETCH_FAILED,
        )

        assertEquals(SyncAuditLogLoadResult.Failed, testee.loadLatest())
    }

    @Test
    fun whenLogIsEmptyThenLoadedWithNoItems() = runTest {
        givenPage(decoded = emptyList())

        assertEquals(SyncAuditLogLoadResult.Loaded(emptyList()), testee.loadLatest())
    }

    @Test
    fun whenLoadedThenLoginsAndLogoutsAreShownNewestFirstAndOtherEntriesAreHidden() = runTest {
        val logout = decoded("e1", LOGOUT, "d1", at = 30)
        val infoAdded = decoded("e2", DEVICE_INFO_ADDED, "d2", at = 20)
        val skipped = Skipped("e3", AuditLogEntrySkipReason.UNKNOWN_TYPE)
        val login = decoded("e4", LOGIN, "d2", at = 10)
        givenPage(decoded = listOf(logout, infoAdded, skipped, login))
        whenever(resolver.resolve(listOf(logout, infoAdded, skipped, login))).thenReturn(
            mapOf(
                "e1" to ResolvedDeviceName("Laptop", "desktop", DeviceNameSource.INFO),
                "e4" to ResolvedDeviceName("Phone", null, DeviceNameSource.LATER_INFO),
            ),
        )

        val result = testee.loadLatest()

        assertEquals(
            SyncAuditLogLoadResult.Loaded(
                listOf(
                    SyncAuditLogItem("e1", REMOVED, Instant.ofEpochSecond(30), "Laptop", DeviceType("desktop")),
                    SyncAuditLogItem("e4", SYNCED, Instant.ofEpochSecond(10), "Phone", DeviceType()),
                ),
            ),
            result,
        )
    }

    @Test
    fun whenNameIsMissingForAnEntryThenThatEntryIsHidden() = runTest {
        val login = decoded("e1", LOGIN, "d1", at = 10)
        givenPage(decoded = listOf(login))
        whenever(resolver.resolve(any())).thenReturn(emptyMap())

        assertEquals(SyncAuditLogLoadResult.Loaded(emptyList()), testee.loadLatest())
    }

    private fun givenToken() {
        whenever(syncStore.token).thenReturn(TOKEN)
    }

    private fun givenPage(
        decoded: List<DecodedAuditLogEntry>,
        keyUnavailableReason: AccountInfoKeyUnavailableReason? = null,
    ) {
        givenToken()
        val rawEntries = decoded.map { AuditLogEntry(id = it.id, rawVersion = "1", payload = "jwe-${it.id}") }
        whenever(syncApi.getAuditLogLatest(TOKEN)).thenReturn(AuditLogResult.Success(AuditLogPage(rawEntries, after = "a", before = null)))
        whenever(decoder.decode(rawEntries)).thenReturn(AuditLogDecodeResult(decoded, keyUnavailableReason))
        whenever(resolver.resolve(decoded)).thenReturn(emptyMap())
    }

    private fun decoded(id: String, type: AuditLogEntryType, deviceId: String, at: Long) = Decoded(
        id = id,
        type = type,
        occurredAt = Instant.ofEpochSecond(at),
        device = DeviceV2(deviceId = deviceId),
        actor = null,
    )

    private companion object {
        const val TOKEN = "token"
    }
}
