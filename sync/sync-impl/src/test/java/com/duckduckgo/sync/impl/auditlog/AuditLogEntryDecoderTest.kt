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
import com.duckduckgo.sync.impl.AccountInfoPrivateKeyProvider
import com.duckduckgo.sync.impl.AccountInfoPrivateKeyResult
import com.duckduckgo.sync.impl.AuditLogEntry
import com.duckduckgo.sync.impl.DeviceV2
import com.duckduckgo.sync.impl.auditlog.AuditLogEntrySkipReason.KEY_NOT_FOUND
import com.duckduckgo.sync.impl.auditlog.AuditLogEntrySkipReason.PAYLOAD_DECRYPT_ERROR
import com.duckduckgo.sync.impl.auditlog.AuditLogEntrySkipReason.UNKNOWN_TYPE
import com.duckduckgo.sync.impl.auditlog.AuditLogEntrySkipReason.UNKNOWN_VERSION
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Decoded
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Skipped
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class AuditLogEntryDecoderTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val privateKeyProvider: AccountInfoPrivateKeyProvider = mock()
    private val syncJweCrypto: SyncJweCrypto = mock()
    private val decoder = RealAuditLogEntryDecoder(privateKeyProvider, syncJweCrypto, coroutineTestRule.testDispatcherProvider)

    @Test
    fun whenLoginPayloadThenDecoded() = runTest {
        givenKeyAvailable()
        givenPayload("jwe-1", """{"type":"login","occurred_at":"2026-09-02T14:03:11Z","device":$DEVICE_A}""")

        val result = decodeEntries(listOf(entry("e1", payload = "jwe-1")))

        assertEquals(
            listOf(Decoded("e1", AuditLogEntryType.LOGIN, Instant.parse("2026-09-02T14:03:11Z"), deviceA, actor = null)),
            result,
        )
    }

    @Test
    fun whenLogoutPayloadThenDecodedWithActor() = runTest {
        givenKeyAvailable()
        givenPayload("jwe-1", """{"type":"logout","occurred_at":"2026-09-02T14:03:11Z","device":$DEVICE_A,"actor":$DEVICE_B}""")

        val result = decodeEntries(listOf(entry("e1", payload = "jwe-1")))

        assertEquals(
            listOf(Decoded("e1", AuditLogEntryType.LOGOUT, Instant.parse("2026-09-02T14:03:11Z"), deviceA, actor = deviceB)),
            result,
        )
    }

    @Test
    fun whenDeviceInfoAddedPayloadThenDecoded() = runTest {
        givenKeyAvailable()
        givenPayload("jwe-1", """{"type":"device_info_added","occurred_at":"2026-09-02T14:03:11Z","device":$DEVICE_A}""")

        val result = decodeEntries(listOf(entry("e1", payload = "jwe-1")))

        assertEquals(AuditLogEntryType.DEVICE_INFO_ADDED, (result.single() as Decoded).type)
    }

    @Test
    fun whenVersionIsNumberThenDecoded() = runTest {
        givenKeyAvailable()
        givenPayload("jwe-1", LOGIN_PAYLOAD)

        val result = decodeEntries(listOf(entry("e1", rawVersion = 1.0, payload = "jwe-1")))

        assertEquals(AuditLogEntryType.LOGIN, (result.single() as Decoded).type)
    }

    @Test
    fun whenVersionIsUnknownThenSkippedWithoutDecrypting() = runTest {
        givenKeyAvailable()

        val result = decodeEntries(listOf(entry("e1", rawVersion = "2"), entry("e2", rawVersion = null)))

        assertEquals(listOf(Skipped("e1", UNKNOWN_VERSION), Skipped("e2", UNKNOWN_VERSION)), result)
        verify(syncJweCrypto, never()).jweDecryptRsaOaep(any(), any())
    }

    @Test
    fun whenKeyUnavailableThenEveryReadableEntrySkippedAsKeyNotFound() = runTest {
        whenever(privateKeyProvider.privateKey())
            .thenReturn(AccountInfoPrivateKeyResult.Unavailable(AccountInfoKeyUnavailableReason.NO_KEY_ON_SERVER))

        val result = decoder.decode(listOf(entry("e1"), entry("e2", rawVersion = "2")))

        assertEquals(
            AuditLogDecodeResult(
                entries = listOf(Skipped("e1", KEY_NOT_FOUND), Skipped("e2", UNKNOWN_VERSION)),
                keyUnavailableReason = AccountInfoKeyUnavailableReason.NO_KEY_ON_SERVER,
            ),
            result,
        )
    }

    @Test
    fun whenKeyFetchIsRateLimitedThenReasonReturnedSoCallerCanRetry() = runTest {
        whenever(privateKeyProvider.privateKey())
            .thenReturn(AccountInfoPrivateKeyResult.Unavailable(AccountInfoKeyUnavailableReason.RATE_LIMITED))

        assertEquals(AccountInfoKeyUnavailableReason.RATE_LIMITED, decoder.decode(listOf(entry("e1"))).keyUnavailableReason)
    }

    @Test
    fun whenKeyAvailableThenNoKeyUnavailableReason() = runTest {
        givenKeyAvailable()
        givenPayload("jwe-1", LOGIN_PAYLOAD)

        assertEquals(null, decoder.decode(listOf(entry("e1", payload = "jwe-1"))).keyUnavailableReason)
    }

    @Test
    fun whenPayloadDoesNotDecryptThenSkipped() = runTest {
        givenKeyAvailable()
        whenever(syncJweCrypto.jweDecryptRsaOaep("jwe-1", PRIVATE_KEY)).thenThrow(RuntimeException("bad tag"))

        assertEquals(listOf(Skipped("e1", PAYLOAD_DECRYPT_ERROR)), decodeEntries(listOf(entry("e1", payload = "jwe-1"))))
    }

    @Test
    fun whenPayloadIsNotJsonThenSkipped() = runTest {
        givenKeyAvailable()
        givenPayload("jwe-1", "{ malformed")

        assertEquals(listOf(Skipped("e1", PAYLOAD_DECRYPT_ERROR)), decodeEntries(listOf(entry("e1", payload = "jwe-1"))))
    }

    @Test
    fun whenPayloadHasNoDeviceIdThenSkipped() = runTest {
        givenKeyAvailable()
        givenPayload("jwe-1", """{"type":"login","occurred_at":"2026-09-02T14:03:11Z","device":{"name":"n"}}""")

        assertEquals(listOf(Skipped("e1", PAYLOAD_DECRYPT_ERROR)), decodeEntries(listOf(entry("e1", payload = "jwe-1"))))
    }

    @Test
    fun whenOccurredAtIsNotATimestampThenSkipped() = runTest {
        givenKeyAvailable()
        givenPayload("jwe-1", """{"type":"login","occurred_at":"yesterday","device":$DEVICE_A}""")

        assertEquals(listOf(Skipped("e1", PAYLOAD_DECRYPT_ERROR)), decodeEntries(listOf(entry("e1", payload = "jwe-1"))))
    }

    @Test
    fun whenTypeIsUnknownThenSkipped() = runTest {
        givenKeyAvailable()
        givenPayload("jwe-1", """{"type":"password_changed","occurred_at":"2026-09-02T14:03:11Z","device":$DEVICE_A}""")

        assertEquals(listOf(Skipped("e1", UNKNOWN_TYPE)), decodeEntries(listOf(entry("e1", payload = "jwe-1"))))
    }

    @Test
    fun whenMixedEntriesThenInputOrderKept() = runTest {
        givenKeyAvailable()
        givenPayload("jwe-1", LOGIN_PAYLOAD)
        givenPayload("jwe-3", LOGIN_PAYLOAD)

        val result = decodeEntries(listOf(entry("e1", payload = "jwe-1"), entry("e2", rawVersion = "9"), entry("e3", payload = "jwe-3")))

        assertEquals(listOf("e1", "e2", "e3"), result.map { it.id })
    }

    @Test
    fun whenManyEntriesThenPrivateKeyFetchedOnce() = runTest {
        givenKeyAvailable()
        givenPayload("jwe-1", LOGIN_PAYLOAD)
        givenPayload("jwe-2", LOGIN_PAYLOAD)

        decoder.decode(listOf(entry("e1", payload = "jwe-1"), entry("e2", payload = "jwe-2")))

        verify(privateKeyProvider, times(1)).privateKey()
    }

    @Test
    fun whenNoEntryNeedsDecryptingThenPrivateKeyNotFetched() = runTest {
        decoder.decode(listOf(entry("e1", rawVersion = "2")))
        decoder.decode(emptyList())

        verify(privateKeyProvider, never()).privateKey()
    }

    private suspend fun decodeEntries(entries: List<AuditLogEntry>) = decoder.decode(entries).entries

    private fun givenKeyAvailable() {
        whenever(privateKeyProvider.privateKey()).thenReturn(AccountInfoPrivateKeyResult.Available(PRIVATE_KEY))
    }

    private fun givenPayload(jwe: String, json: String) {
        whenever(syncJweCrypto.jweDecryptRsaOaep(jwe, PRIVATE_KEY)).thenReturn(json.toByteArray(Charsets.UTF_8))
    }

    private fun entry(id: String, rawVersion: Any? = "1", payload: String = "jwe") = AuditLogEntry(
        id = id,
        rawVersion = rawVersion,
        payload = payload,
    )

    private val deviceA =
        DeviceV2(deviceId = "device-a", deviceName = "enc-name-a", deviceType = "enc-type-a", deviceInfo = "info-a", credentialId = "ddg")
    private val deviceB = DeviceV2(deviceId = "device-b", deviceName = "enc-name-b", credentialId = "3party")

    private companion object {
        const val PRIVATE_KEY = "private-key"
        const val DEVICE_A = """{"id":"device-a","name":"enc-name-a","type":"enc-type-a","info":"info-a","credential_id":"ddg"}"""
        const val DEVICE_B = """{"id":"device-b","name":"enc-name-b","credential_id":"3party"}"""
        const val LOGIN_PAYLOAD = """{"type":"login","occurred_at":"2026-09-02T14:03:11Z","device":$DEVICE_A}"""
    }
}
