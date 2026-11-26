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
import com.duckduckgo.sync.impl.auditlog.AuditLogPixels
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
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
    private val auditLogPixels: AuditLogPixels = mock()

    private val testee = RealSyncAuditLogLoader(
        syncStore = syncStore,
        syncApi = syncApi,
        entryDecoder = decoder,
        nameResolver = resolver,
        auditLogPixels = auditLogPixels,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Before
    fun setup() = runTest {
        whenever(resolver.resolve(any(), any())).thenReturn(emptyMap())
    }

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
    fun whenServerFailsThenFetchErrorPixelFiredWithStatusCode() = runTest {
        givenToken()
        whenever(syncApi.getAuditLogLatest(TOKEN)).thenReturn(AuditLogResult.ServerError(500))

        testee.loadLatest()

        verify(auditLogPixels).fireFetchError(500)
    }

    @Test
    fun whenNoConnectionThenFetchErrorPixelFiredWithoutStatusCode() = runTest {
        givenToken()
        whenever(syncApi.getAuditLogLatest(TOKEN)).thenReturn(AuditLogResult.NetworkError)

        testee.loadLatest()

        verify(auditLogPixels).fireFetchError(null)
    }

    @Test
    fun whenLoadOlderFailsThenFetchErrorPixelFiredWithStatusCode() = runTest {
        givenToken()
        whenever(syncApi.getAuditLogBefore(TOKEN, "b1")).thenReturn(AuditLogResult.RateLimited(418))

        testee.loadOlder(loaded(emptyList(), before = "b1"))

        verify(auditLogPixels).fireFetchError(418)
    }

    @Test
    fun whenLoadSucceedsThenNoPixel() = runTest {
        givenPage(decoded = emptyList())

        testee.loadLatest()

        verifyNoInteractions(auditLogPixels)
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

        assertEquals(SyncAuditLogLoadResult.Loaded(emptyList(), SyncAuditLogPages(emptyList(), before = null)), testee.loadLatest())
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

        val result = testee.loadLatest() as SyncAuditLogLoadResult.Loaded

        assertEquals(
            listOf(
                SyncAuditLogItem("e1", REMOVED, Instant.ofEpochSecond(30), "Laptop", DeviceType("desktop")),
                SyncAuditLogItem("e4", SYNCED, Instant.ofEpochSecond(10), "Phone", DeviceType()),
            ),
            result.items,
        )
    }

    @Test
    fun whenNameIsMissingForAnEntryThenThatEntryIsHidden() = runTest {
        val login = decoded("e1", LOGIN, "d1", at = 10)
        givenPage(decoded = listOf(login))
        whenever(resolver.resolve(any(), any())).thenReturn(emptyMap())

        assertEquals(emptyList<SyncAuditLogItem>(), (testee.loadLatest() as SyncAuditLogLoadResult.Loaded).items)
    }

    @Test
    fun whenFirstPageHasMoreThenLoadedKeepsEntriesAndCursor() = runTest {
        val login = decoded("e1", LOGIN, "d1", at = 10)
        givenPage(decoded = listOf(login), before = "b1")
        givenName(login)

        val result = testee.loadLatest() as SyncAuditLogLoadResult.Loaded

        assertEquals(SyncAuditLogPages(listOf(login), before = "b1"), result.pages)
        assertTrue(result.hasMore)
    }

    @Test
    fun whenPageHasNoVisibleRowsThenOlderPagesLoadedUntilARow() = runTest {
        val infoAdded = decoded("e3", DEVICE_INFO_ADDED, "d1", at = 30)
        val skipped = Skipped("e2", AuditLogEntrySkipReason.UNKNOWN_TYPE)
        val login = decoded("e1", LOGIN, "d1", at = 10)
        givenPage(decoded = listOf(infoAdded), before = "b1")
        givenOlderPage(cursor = "b1", decoded = listOf(skipped), before = "b2")
        givenOlderPage(cursor = "b2", decoded = listOf(login), before = "b3")
        givenName(login, newerEntries = listOf(infoAdded, skipped))

        val result = testee.loadLatest() as SyncAuditLogLoadResult.Loaded

        assertEquals(listOf("e1"), result.items.map { it.entryId })
        assertEquals(SyncAuditLogPages(listOf(infoAdded, skipped, login), before = "b3"), result.pages)
    }

    @Test
    fun whenNoPageHasVisibleRowsThenLoadedEmptyWithNoMore() = runTest {
        val infoAdded = decoded("e2", DEVICE_INFO_ADDED, "d1", at = 20)
        givenPage(decoded = listOf(infoAdded), before = "b1")
        givenOlderPage(cursor = "b1", decoded = emptyList(), before = null)

        val result = testee.loadLatest() as SyncAuditLogLoadResult.Loaded

        assertEquals(emptyList<SyncAuditLogItem>(), result.items)
        assertFalse(result.hasMore)
    }

    @Test
    fun whenManyPagesHaveNoVisibleRowsThenStopsAtPageLimitWithMore() = runTest {
        givenPage(decoded = listOf(decoded("p0", DEVICE_INFO_ADDED, "d1", at = 100)), before = "b1")
        (1..RealSyncAuditLogLoader.MAX_PAGES_PER_LOAD).forEach { page ->
            givenOlderPage(cursor = "b$page", decoded = listOf(decoded("p$page", DEVICE_INFO_ADDED, "d1", at = 100L - page)), before = "b${page + 1}")
        }

        val result = testee.loadLatest() as SyncAuditLogLoadResult.Loaded

        assertEquals(emptyList<SyncAuditLogItem>(), result.items)
        assertEquals("b${RealSyncAuditLogLoader.MAX_PAGES_PER_LOAD}", result.pages.before)
        verify(syncApi, times(RealSyncAuditLogLoader.MAX_PAGES_PER_LOAD - 1)).getAuditLogBefore(eq(TOKEN), any(), anyOrNull())
    }

    @Test
    fun whenLoadOlderThenOnlyOlderRowsReturnedAndNamedWithNewerEntries() = runTest {
        val newerLogin = decoded("e3", LOGIN, "d2", at = 30)
        val infoAdded = decoded("e2", DEVICE_INFO_ADDED, "d1", at = 20)
        val olderLogin = decoded("e1", LOGIN, "d1", at = 10)
        givenToken()
        givenOlderPage(cursor = "b1", decoded = listOf(olderLogin), before = null)
        givenName(olderLogin, newerEntries = listOf(newerLogin, infoAdded))

        val result = testee.loadOlder(loaded(listOf(newerLogin, infoAdded), before = "b1")) as SyncAuditLogLoadResult.Loaded

        assertEquals(listOf("e1"), result.items.map { it.entryId })
        assertEquals(SyncAuditLogPages(listOf(newerLogin, infoAdded, olderLogin), before = null), result.pages)
        assertFalse(result.hasMore)
    }

    @Test
    fun whenOlderPageRepeatsALoadedEntryThenItIsDropped() = runTest {
        val newerLogin = decoded("e2", LOGIN, "d2", at = 20)
        val olderLogin = decoded("e1", LOGIN, "d1", at = 10)
        givenToken()
        givenOlderPage(cursor = "b1", decoded = listOf(newerLogin, olderLogin), before = null)
        givenName(olderLogin, newerEntries = listOf(newerLogin))

        val result = testee.loadOlder(loaded(listOf(newerLogin), before = "b1")) as SyncAuditLogLoadResult.Loaded

        assertEquals(listOf("e1"), result.items.map { it.entryId })
        assertEquals(listOf(newerLogin, olderLogin), result.pages.entries)
    }

    @Test
    fun whenLoadOlderFailsThenFails() = runTest {
        givenToken()
        whenever(syncApi.getAuditLogBefore(TOKEN, "b1")).thenReturn(AuditLogResult.RateLimited(418))

        assertEquals(SyncAuditLogLoadResult.Failed, testee.loadOlder(loaded(emptyList(), before = "b1")))
    }

    @Test
    fun whenLoadOlderHasNoKeyThenFails() = runTest {
        givenToken()
        givenOlderPage(
            cursor = "b1",
            decoded = listOf(Skipped("e1", AuditLogEntrySkipReason.KEY_NOT_FOUND)),
            before = null,
            keyUnavailableReason = AccountInfoKeyUnavailableReason.KEYS_FETCH_FAILED,
        )

        assertEquals(SyncAuditLogLoadResult.Failed, testee.loadOlder(loaded(emptyList(), before = "b1")))
    }

    @Test
    fun whenNothingOlderThenLoadOlderDoesNotCallServer() = runTest {
        givenToken()
        val loaded = loaded(emptyList(), before = null)

        assertEquals(loaded.copy(items = emptyList()), testee.loadOlder(loaded))
        verifyNoInteractions(syncApi)
    }

    private fun givenToken() {
        whenever(syncStore.token).thenReturn(TOKEN)
    }

    private suspend fun givenPage(
        decoded: List<DecodedAuditLogEntry>,
        before: String? = null,
        keyUnavailableReason: AccountInfoKeyUnavailableReason? = null,
    ) {
        givenToken()
        val page = givenDecodedPage(decoded, before, keyUnavailableReason)
        whenever(syncApi.getAuditLogLatest(TOKEN)).thenReturn(AuditLogResult.Success(page))
    }

    private suspend fun givenOlderPage(
        cursor: String,
        decoded: List<DecodedAuditLogEntry>,
        before: String?,
        keyUnavailableReason: AccountInfoKeyUnavailableReason? = null,
    ) {
        val page = givenDecodedPage(decoded, before, keyUnavailableReason)
        whenever(syncApi.getAuditLogBefore(TOKEN, cursor)).thenReturn(AuditLogResult.Success(page))
    }

    private suspend fun givenDecodedPage(
        decoded: List<DecodedAuditLogEntry>,
        before: String?,
        keyUnavailableReason: AccountInfoKeyUnavailableReason?,
    ): AuditLogPage {
        val rawEntries = decoded.map { AuditLogEntry(id = it.id, rawVersion = "1", payload = "jwe-${it.id}") }
        whenever(decoder.decode(rawEntries)).thenReturn(AuditLogDecodeResult(decoded, keyUnavailableReason))
        return AuditLogPage(rawEntries, after = "a", before = before)
    }

    private suspend fun givenName(entry: Decoded, newerEntries: List<DecodedAuditLogEntry> = emptyList()) {
        whenever(resolver.resolve(listOf(entry), newerEntries))
            .thenReturn(mapOf(entry.id to ResolvedDeviceName("Name ${entry.id}", null, DeviceNameSource.INFO)))
    }

    private fun loaded(entries: List<DecodedAuditLogEntry>, before: String?) =
        SyncAuditLogLoadResult.Loaded(items = listOf(), pages = SyncAuditLogPages(entries, before))

    private fun decoded(id: String, type: AuditLogEntryType, deviceId: String, at: Long) = Decoded(
        id = id,
        type = type,
        occurredAt = Instant.ofEpochSecond(at),
        device = DeviceV2(deviceId = deviceId),
        actor = null,
    )

    @Test
    fun whenEntriesPerPageGivenThenEveryPageRequestsIt() = runTest {
        val login = decoded("e1", LOGIN, "d1", at = 10)
        givenToken()
        val page = givenDecodedPage(listOf(decoded("e2", DEVICE_INFO_ADDED, "d1", at = 20)), before = "b1", keyUnavailableReason = null)
        whenever(syncApi.getAuditLogLatest(TOKEN, 5)).thenReturn(AuditLogResult.Success(page))
        val older = givenDecodedPage(listOf(login), before = null, keyUnavailableReason = null)
        whenever(syncApi.getAuditLogBefore(TOKEN, "b1", 5)).thenReturn(AuditLogResult.Success(older))

        val result = testee.loadLatest(entriesPerPage = 5) as SyncAuditLogLoadResult.Loaded

        assertEquals(listOf("e2", "e1"), result.pages.entries.map { it.id })
    }

    @Test
    fun whenLoadOlderWithEntriesPerPageThenRequestsIt() = runTest {
        val login = decoded("e1", LOGIN, "d1", at = 10)
        givenToken()
        val older = givenDecodedPage(listOf(login), before = null, keyUnavailableReason = null)
        whenever(syncApi.getAuditLogBefore(TOKEN, "b1", 5)).thenReturn(AuditLogResult.Success(older))

        val result = testee.loadOlder(loaded(emptyList(), before = "b1"), entriesPerPage = 5) as SyncAuditLogLoadResult.Loaded

        assertEquals(listOf(login), result.pages.entries)
    }

    private companion object {
        const val TOKEN = "token"
    }
}
