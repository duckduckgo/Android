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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.AccountInfoKeyUnavailableReason
import com.duckduckgo.sync.impl.AuditLogEntry
import com.duckduckgo.sync.impl.AuditLogPage
import com.duckduckgo.sync.impl.DeviceFieldDecryptor
import com.duckduckgo.sync.impl.DeviceInfoDecryptor
import com.duckduckgo.sync.impl.DeviceInfoPayload
import com.duckduckgo.sync.impl.DeviceInfoSessionResult
import com.duckduckgo.sync.impl.DeviceV2
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.impl.ThirdPartyDeviceListDecryptor.Companion.FALLBACK_NAME
import com.duckduckgo.sync.impl.auditlog.AuditLogCheckResult.FailureReason
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.DEVICE_INFO_ADDED
import com.duckduckgo.sync.impl.auditlog.AuditLogEntryType.LOGIN
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Decoded
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Skipped
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Instant

class RealAuditLogCheckerTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val syncStore = FakeSyncStore()
    private val syncApi: SyncApi = mock()
    private val feature = FakeLoginAuditLogFeature()
    private val decoder = FakeDecoder()
    private val deviceFieldDecryptor: DeviceFieldDecryptor = mock()
    private val deviceInfoDecryptor = FakeDeviceInfoDecryptor()
    private val stateStore = RealAuditLogCheckStateStore(syncStore, coroutineTestRule.testDispatcherProvider)

    private val testee = RealAuditLogChecker(
        syncStore = syncStore,
        syncApi = syncApi,
        loginAuditLogFeature = feature,
        stateStore = stateStore,
        entryDecoder = decoder,
        pendingLoginsUpdater = RealPendingLoginsUpdater(
            RealAuditLogDeviceNameResolver(deviceInfoDecryptor, deviceFieldDecryptor),
            deviceInfoDecryptor,
        ),
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Before
    fun setup() {
        whenever(deviceFieldDecryptor.decrypt(any())).thenReturn(Result.Error(reason = "no legacy name"))
    }

    @Test
    fun whenNoCursorThenInitStoresCursorAndAddsNothing() = runTest {
        whenever(syncApi.getAuditLogLatest(TOKEN, 1)).thenReturn(page(after = "c1", login("e1", OTHER_DEVICE)))

        val result = testee.check()

        assertEquals(AuditLogCheckResult.Initialised, result)
        assertEquals(AuditLogCheckState(cursor = "c1", pendingLogins = emptyList()), stateStore.read())
        verify(syncApi, never()).getAuditLogAfter(any(), any())
    }

    @Test
    fun whenInitResponseHasNoCursorThenNothingIsStored() = runTest {
        whenever(syncApi.getAuditLogLatest(TOKEN, 1)).thenReturn(page(after = null))

        val result = testee.check()

        assertEquals(AuditLogCheckResult.Failed(FailureReason.SERVER), result)
        assertNull(stateStore.read().cursor)
    }

    @Test
    fun whenCheckFindsLoginFromOtherDeviceThenItIsAddedAndCursorMoves() = runTest {
        givenCursor("c1")
        whenever(syncApi.getAuditLogAfter(TOKEN, "c1")).thenReturn(page(after = "c2", login("e1", OTHER_DEVICE, info = "info:Laptop", at = 10)))
        whenever(syncApi.getAuditLogAfter(TOKEN, "c2")).thenReturn(page(after = "c2"))

        val result = testee.check()

        assertEquals(AuditLogCheckResult.Checked(newLogins = 1, renamedLogins = 0), result)
        assertEquals(AuditLogCheckState(cursor = "c2", pendingLogins = listOf(pending("e1", OTHER_DEVICE, "Laptop", 10))), stateStore.read())
    }

    @Test
    fun whenCheckFindsNothingNewThenStateIsUnchanged() = runTest {
        givenCursor("c1")
        whenever(syncApi.getAuditLogAfter(TOKEN, "c1")).thenReturn(page(after = "c1"))

        val result = testee.check()

        assertEquals(AuditLogCheckResult.Checked(newLogins = 0, renamedLogins = 0), result)
        assertEquals(AuditLogCheckState(cursor = "c1"), stateStore.read())
    }

    @Test
    fun whenCheckSpansPagesThenLoginsAreOldestFirstAndNameFromLaterPageIsUsed() = runTest {
        givenCursor("c1")
        whenever(syncApi.getAuditLogAfter(TOKEN, "c1")).thenReturn(
            page(after = "c2", login("e2", THIRD_DEVICE, at = 2), login("e1", OTHER_DEVICE, at = 1)),
        )
        whenever(syncApi.getAuditLogAfter(TOKEN, "c2")).thenReturn(
            page(after = "c3", infoAdded("e3", OTHER_DEVICE, info = "info:Laptop", at = 3)),
        )
        whenever(syncApi.getAuditLogAfter(TOKEN, "c3")).thenReturn(page(after = "c3"))

        testee.check()

        assertEquals(
            AuditLogCheckState(
                cursor = "c3",
                pendingLogins = listOf(pending("e1", OTHER_DEVICE, "Laptop", 1), pending("e2", THIRD_DEVICE, FALLBACK_NAME, 2)),
            ),
            stateStore.read(),
        )
    }

    @Test
    fun whenDeviceInfoAddedArrivesInLaterCheckThenLatestPendingRowForThatDeviceIsRenamed() = runTest {
        givenState(
            cursor = "c1",
            pending("e1", OTHER_DEVICE, FALLBACK_NAME, 1),
            pending("e2", OTHER_DEVICE, FALLBACK_NAME, 2),
            pending("e3", THIRD_DEVICE, FALLBACK_NAME, 3),
        )
        whenever(syncApi.getAuditLogAfter(TOKEN, "c1")).thenReturn(page(after = "c2", infoAdded("e4", OTHER_DEVICE, info = "info:Laptop")))
        whenever(syncApi.getAuditLogAfter(TOKEN, "c2")).thenReturn(page(after = "c2"))

        val result = testee.check()

        assertEquals(AuditLogCheckResult.Checked(newLogins = 0, renamedLogins = 1), result)
        assertEquals(
            listOf(
                pending("e1", OTHER_DEVICE, FALLBACK_NAME, 1),
                pending("e2", OTHER_DEVICE, "Laptop", 2),
                pending("e3", THIRD_DEVICE, FALLBACK_NAME, 3),
            ),
            stateStore.read().pendingLogins,
        )
    }

    @Test
    fun whenMoreThanMaxPendingLoginsThenNewestAreKept() = runTest {
        givenCursor("c1")
        val logins = (1..AuditLogCheckStateStore.MAX_PENDING_LOGINS + 5).map { login("e$it", OTHER_DEVICE, info = "info:Laptop", at = it.toLong()) }
        whenever(syncApi.getAuditLogAfter(TOKEN, "c1")).thenReturn(page("c2", *logins.reversed().toTypedArray()))
        whenever(syncApi.getAuditLogAfter(TOKEN, "c2")).thenReturn(page(after = "c2"))

        testee.check()

        val stored = stateStore.read().pendingLogins
        assertEquals(AuditLogCheckStateStore.MAX_PENDING_LOGINS, stored.size)
        assertEquals("e6", stored.first().entryId)
        assertEquals("e105", stored.last().entryId)
    }

    @Test
    fun whenCursorIsInvalidThenInitRunsAndPendingLoginsAreKept() = runTest {
        givenState(cursor = "bad", pending("e1", OTHER_DEVICE, "Laptop", 1))
        whenever(syncApi.getAuditLogAfter(TOKEN, "bad")).thenReturn(AuditLogResult.InvalidCursor)
        whenever(syncApi.getAuditLogLatest(TOKEN, 1)).thenReturn(page(after = "fresh"))

        val result = testee.check()

        assertEquals(AuditLogCheckResult.Initialised, result)
        assertEquals(AuditLogCheckState(cursor = "fresh", pendingLogins = listOf(pending("e1", OTHER_DEVICE, "Laptop", 1))), stateStore.read())
    }

    @Test
    fun whenCursorIsInvalidAndInitFailsThenCursorIsCleared() = runTest {
        givenState(cursor = "bad", pending("e1", OTHER_DEVICE, "Laptop", 1))
        whenever(syncApi.getAuditLogAfter(TOKEN, "bad")).thenReturn(AuditLogResult.InvalidCursor)
        whenever(syncApi.getAuditLogLatest(TOKEN, 1)).thenReturn(AuditLogResult.NetworkError)

        val result = testee.check()

        assertEquals(AuditLogCheckResult.Failed(FailureReason.NETWORK), result)
        assertEquals(AuditLogCheckState(cursor = null, pendingLogins = listOf(pending("e1", OTHER_DEVICE, "Laptop", 1))), stateStore.read())
    }

    @Test
    fun whenRequestFailsThenStateIsUnchanged() = runTest {
        val failures = mapOf(
            AuditLogResult.RateLimited(418) to FailureReason.RATE_LIMITED,
            AuditLogResult.ServerError(500) to FailureReason.SERVER,
            AuditLogResult.NetworkError to FailureReason.NETWORK,
            AuditLogResult.AuthError(401) to FailureReason.AUTH,
        )
        failures.forEach { (apiResult, reason) ->
            givenState(cursor = "c1", pending("e1", OTHER_DEVICE, "Laptop", 1))
            whenever(syncApi.getAuditLogAfter(TOKEN, "c1")).thenReturn(apiResult)

            assertEquals(AuditLogCheckResult.Failed(reason), testee.check())
            assertEquals(AuditLogCheckState(cursor = "c1", pendingLogins = listOf(pending("e1", OTHER_DEVICE, "Laptop", 1))), stateStore.read())
        }
    }

    @Test
    fun whenLaterPageFailsThenEarlierPagesAreNotStored() = runTest {
        givenCursor("c1")
        whenever(syncApi.getAuditLogAfter(TOKEN, "c1")).thenReturn(page(after = "c2", login("e1", OTHER_DEVICE, info = "info:Laptop")))
        whenever(syncApi.getAuditLogAfter(TOKEN, "c2")).thenReturn(AuditLogResult.RateLimited(418))

        val result = testee.check()

        assertEquals(AuditLogCheckResult.Failed(FailureReason.RATE_LIMITED), result)
        assertEquals(AuditLogCheckState(cursor = "c1"), stateStore.read())
    }

    @Test
    fun whenAccountInfoKeyIsTemporarilyUnavailableThenStateIsUnchanged() = runTest {
        listOf(AccountInfoKeyUnavailableReason.KEYS_FETCH_FAILED, AccountInfoKeyUnavailableReason.RATE_LIMITED).forEach { reason ->
            givenCursor("c1")
            decoder.keyUnavailableReason = reason
            whenever(syncApi.getAuditLogAfter(TOKEN, "c1")).thenReturn(page(after = "c2", login("e1", OTHER_DEVICE)))
            whenever(syncApi.getAuditLogAfter(TOKEN, "c2")).thenReturn(page(after = "c2"))

            assertEquals(AuditLogCheckResult.Failed(FailureReason.KEY_UNAVAILABLE), testee.check())
            assertEquals(AuditLogCheckState(cursor = "c1"), stateStore.read())
        }
    }

    @Test
    fun whenAccountInfoKeyIsPermanentlyUnavailableThenCursorMovesOn() = runTest {
        givenCursor("c1")
        decoder.keyUnavailableReason = AccountInfoKeyUnavailableReason.UNWRAP_FAILED
        whenever(syncApi.getAuditLogAfter(TOKEN, "c1")).thenReturn(page(after = "c2", login("e1", OTHER_DEVICE)))
        whenever(syncApi.getAuditLogAfter(TOKEN, "c2")).thenReturn(page(after = "c2"))

        assertEquals(AuditLogCheckResult.Checked(newLogins = 0, renamedLogins = 0), testee.check())
        assertEquals(AuditLogCheckState(cursor = "c2"), stateStore.read())
    }

    @Test
    fun whenPagesNeverEndThenCheckStopsAtPageLimitAndStoresProgress() = runTest {
        givenCursor("c0")
        whenever(syncApi.getAuditLogAfter(any(), any())).thenAnswer {
            val n = (it.getArgument<String>(1)).removePrefix("c").toInt() + 1
            page(after = "c$n", login("e$n", OTHER_DEVICE, info = "info:Laptop", at = n.toLong()))
        }

        testee.check()

        val state = stateStore.read()
        assertEquals("c${RealAuditLogChecker.MAX_PAGES_PER_CHECK}", state.cursor)
        assertEquals(RealAuditLogChecker.MAX_PAGES_PER_CHECK, state.pendingLogins.size)
    }

    @Test
    fun whenDeviceIdChangesDuringCheckThenNothingIsWritten() = runTest {
        givenCursor("c1")
        whenever(syncApi.getAuditLogAfter(TOKEN, "c1")).thenAnswer {
            syncStore.deviceId = "new-device"
            page(after = "c2", login("e1", OTHER_DEVICE, info = "info:Laptop"))
        }
        whenever(syncApi.getAuditLogAfter(TOKEN, "c2")).thenReturn(page(after = "c2"))

        val result = testee.check()

        assertEquals(AuditLogCheckResult.Discarded, result)
        assertEquals(AuditLogCheckState(cursor = "c1"), stateStore.read())
    }

    @Test
    fun whenSignedOutDuringCheckThenNothingIsWritten() = runTest {
        givenCursor("c1")
        whenever(syncApi.getAuditLogAfter(TOKEN, "c1")).thenAnswer {
            syncStore.signedIn = false
            page(after = "c2", login("e1", OTHER_DEVICE, info = "info:Laptop"))
        }
        whenever(syncApi.getAuditLogAfter(TOKEN, "c2")).thenReturn(page(after = "c2"))

        val result = testee.check()

        assertEquals(AuditLogCheckResult.Discarded, result)
        assertEquals(AuditLogCheckState(cursor = "c1"), stateStore.read())
    }

    @Test
    fun whenCursorChangesDuringCheckThenNothingIsWritten() = runTest {
        givenCursor("c1")
        whenever(syncApi.getAuditLogAfter(TOKEN, "c1")).thenAnswer {
            syncStore.auditLogCursor = "other"
            page(after = "c2", login("e1", OTHER_DEVICE, info = "info:Laptop"))
        }
        whenever(syncApi.getAuditLogAfter(TOKEN, "c2")).thenReturn(page(after = "c2"))

        val result = testee.check()

        assertEquals(AuditLogCheckResult.Discarded, result)
        assertEquals(AuditLogCheckState(cursor = "other"), stateStore.read())
    }

    @Test
    fun whenSignedOutThenCheckDoesNothing() = runTest {
        syncStore.signedIn = false

        val result = testee.check()

        assertEquals(AuditLogCheckResult.NotSignedIn, result)
        verifyNoInteractions(syncApi)
    }

    @Test
    fun whenNotificationFeatureIsOffThenCheckDoesNothingAndKeepsState() = runTest {
        givenState(cursor = "c1", pending("e1", OTHER_DEVICE, "Laptop", 1))
        feature.notificationEnabled = false

        val result = testee.check()

        assertEquals(AuditLogCheckResult.Disabled, result)
        verifyNoInteractions(syncApi)
        assertEquals(AuditLogCheckState(cursor = "c1", pendingLogins = listOf(pending("e1", OTHER_DEVICE, "Laptop", 1))), stateStore.read())
    }

    @Test
    fun whenUnexpectedExceptionThenCheckReturnsFailure() = runTest {
        givenCursor("c1")
        whenever(syncApi.getAuditLogAfter(TOKEN, "c1")).thenThrow(IllegalStateException("boom"))

        val result = testee.check()

        assertEquals(AuditLogCheckResult.Failed(FailureReason.UNEXPECTED), result)
        assertEquals(AuditLogCheckState(cursor = "c1"), stateStore.read())
    }

    private suspend fun givenCursor(cursor: String) = givenState(cursor)

    private suspend fun givenState(cursor: String, vararg logins: PendingLogin) {
        stateStore.update { AuditLogCheckState(cursor = cursor, pendingLogins = logins.toList()) }
    }

    /** Entries newest first, as the server sends them. */
    private fun page(after: String?, vararg entries: DecodedAuditLogEntry): AuditLogResult {
        entries.forEach { decoder.register(it) }
        val raw = entries.map { AuditLogEntry(id = it.id, rawVersion = "1", payload = "payload-${it.id}") }
        return AuditLogResult.Success(AuditLogPage(entries = raw, after = after))
    }

    private fun login(id: String, deviceId: String, info: String? = null, at: Long = 0) =
        Decoded(id, LOGIN, Instant.ofEpochSecond(at), DeviceV2(deviceId = deviceId, deviceInfo = info), null)

    private fun infoAdded(id: String, deviceId: String, info: String, at: Long = 0) =
        Decoded(id, DEVICE_INFO_ADDED, Instant.ofEpochSecond(at), DeviceV2(deviceId = deviceId, deviceInfo = info), null)

    private fun pending(id: String, deviceId: String, name: String, at: Long) = PendingLogin(id, deviceId, name, Instant.ofEpochSecond(at))

    private class FakeDecoder : AuditLogEntryDecoder {
        private val byId = mutableMapOf<String, DecodedAuditLogEntry>()
        var keyUnavailableReason: AccountInfoKeyUnavailableReason? = null

        fun register(entry: DecodedAuditLogEntry) {
            byId[entry.id] = entry
        }

        override fun decode(entries: List<AuditLogEntry>): AuditLogDecodeResult {
            val reason = keyUnavailableReason
            val decoded = entries.map { if (reason != null) Skipped(it.id, AuditLogEntrySkipReason.KEY_NOT_FOUND) else byId.getValue(it.id) }
            return AuditLogDecodeResult(decoded, reason)
        }
    }

    /** `info:<name>` decrypts to `<name>`; anything else fails. */
    private class FakeDeviceInfoDecryptor : DeviceInfoDecryptor {
        override fun openSession(): DeviceInfoSessionResult = DeviceInfoSessionResult.Available(
            object : DeviceInfoDecryptor.Session {
                override fun decrypt(deviceInfoJwe: String): Result<DeviceInfoPayload> {
                    return if (deviceInfoJwe.startsWith("info:")) {
                        Result.Success(DeviceInfoPayload(name = deviceInfoJwe.removePrefix("info:")))
                    } else {
                        Result.Error(reason = "not info")
                    }
                }
            },
        )
    }

    private class FakeLoginAuditLogFeature : LoginAuditLogFeature {
        var notificationEnabled = true
        override suspend fun isAuditLogEnabled(): Boolean = true
        override suspend fun isNotificationEnabled(): Boolean = notificationEnabled
    }

    private class FakeSyncStore : SyncStore by mock<SyncStore>() {
        @Volatile var signedIn = true

        @Volatile override var token: String? = TOKEN

        @Volatile override var deviceId: String? = OWN_DEVICE

        @Volatile override var auditLogCursor: String? = null

        @Volatile override var auditLogPending: String? = null

        override fun isSignedIn(): Boolean = signedIn
    }

    private companion object {
        const val TOKEN = "token"
        const val OWN_DEVICE = "own-device"
        const val OTHER_DEVICE = "other-device"
        const val THIRD_DEVICE = "third-device"
    }
}
