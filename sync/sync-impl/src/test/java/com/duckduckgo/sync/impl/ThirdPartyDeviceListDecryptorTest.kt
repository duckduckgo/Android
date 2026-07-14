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

package com.duckduckgo.sync.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.ThirdPartyDeviceListDecryptor.Companion.FALLBACK_NAME
import com.duckduckgo.sync.impl.ThirdPartyDeviceListDecryptor.Companion.FALLBACK_TYPE_3PARTY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ThirdPartyDeviceListDecryptorTest {

    private val fieldDecryptor: DeviceFieldDecryptor = mock()
    private val thirdPartyCredentialManager: ThirdPartyCredentialManager = mock()

    private lateinit var decryptor: ThirdPartyDeviceListDecryptor

    @Before
    fun before() {
        decryptor = RealThirdPartyDeviceListDecryptor(fieldDecryptor, thirdPartyCredentialManager)
    }

    @Test
    fun whenInputIsEmptyThenReturnsEmptyAndNoRefreshCall() {
        val result = decryptor.decryptAll(emptyList())

        assertTrue(result.decrypted.isEmpty())
        assertTrue(result.undecryptable.isEmpty())
        verify(thirdPartyCredentialManager, never()).refresh()
    }

    @Test
    fun whenAllEntriesDecryptSuccessfullyThenNoRefreshAndNoLogout() {
        val ddg = DeviceV2(deviceId = "d1", credentialId = "ddg", deviceName = "n")
        val tp = DeviceV2(deviceId = "d2", credentialId = "3party", deviceName = "n")
        whenever(fieldDecryptor.decrypt(ddg)).thenReturn(Success(DecryptedDevice("d1", "Pixel 7", "phone")))
        whenever(fieldDecryptor.decrypt(tp)).thenReturn(Success(DecryptedDevice("d2", "Chrome", "Browser")))

        val result = decryptor.decryptAll(listOf(ddg, tp))

        assertEquals(2, result.decrypted.size)
        assertTrue(result.undecryptable.isEmpty())
        verify(thirdPartyCredentialManager, never()).refresh()
    }

    @Test
    fun when3PartyFailsButRefreshRecoversThenSuccessAndNoLogout() {
        val tp = DeviceV2(deviceId = "d1", credentialId = "3party", deviceName = "ENC")
        whenever(fieldDecryptor.decrypt(tp))
            .thenReturn(Error(reason = "stale SP"))
            .thenReturn(Success(DecryptedDevice("d1", "Chrome", "Browser")))
        whenever(thirdPartyCredentialManager.refresh()).thenReturn(Success(true))

        val result = decryptor.decryptAll(listOf(tp))

        assertEquals(listOf(DecryptedDevice("d1", "Chrome", "Browser")), result.decrypted)
        assertTrue(result.undecryptable.isEmpty())
        verify(thirdPartyCredentialManager, times(1)).refresh()
        verify(fieldDecryptor, times(2)).decrypt(tp)
    }

    @Test
    fun when3PartyFailsAndRefreshConfirmsFreshSpAndRetryStillFailsThenAddedToLogoutList() {
        val tp = DeviceV2(deviceId = "d1", credentialId = "3party", deviceName = "ENC")
        whenever(fieldDecryptor.decrypt(tp)).thenReturn(Error(reason = "still bad"))
        whenever(thirdPartyCredentialManager.refresh()).thenReturn(Success(true))

        val result = decryptor.decryptAll(listOf(tp))

        assertTrue(result.decrypted.isEmpty())
        assertEquals(listOf("d1"), result.undecryptable)
        verify(thirdPartyCredentialManager, times(1)).refresh()
    }

    @Test
    fun when3PartyFailsAndRefreshReturnsErrorThenEntryFallsBackToUnknownNotLogout() {
        val tp = DeviceV2(deviceId = "d1", credentialId = "3party", deviceName = "ENC")
        whenever(fieldDecryptor.decrypt(tp)).thenReturn(Error(reason = "still bad"))
        whenever(thirdPartyCredentialManager.refresh()).thenReturn(Error(reason = "server down"))

        val result = decryptor.decryptAll(listOf(tp))

        assertEquals(listOf(DecryptedDevice("d1", FALLBACK_NAME, FALLBACK_TYPE_3PARTY)), result.decrypted)
        assertTrue(result.undecryptable.isEmpty())
        verify(thirdPartyCredentialManager, times(1)).refresh()
    }

    @Test
    fun when3PartyFailsAndRefreshReturnsSuccessFalseThenEntryFallsBackToUnknownNotLogout() {
        val tp = DeviceV2(deviceId = "d1", credentialId = "3party", deviceName = "ENC")
        whenever(fieldDecryptor.decrypt(tp)).thenReturn(Error(reason = "still bad"))
        whenever(thirdPartyCredentialManager.refresh()).thenReturn(Success(false))

        val result = decryptor.decryptAll(listOf(tp))

        assertEquals(listOf(DecryptedDevice("d1", FALLBACK_NAME, FALLBACK_TYPE_3PARTY)), result.decrypted)
        assertTrue(result.undecryptable.isEmpty())
        verify(thirdPartyCredentialManager, times(1)).refresh()
    }

    @Test
    fun whenMultiple3PartyEntriesFailAndRefreshConfirmsFreshSpThenAllAddedToLogout() {
        val tp1 = DeviceV2(deviceId = "d1", credentialId = "3party", deviceName = "ENC1")
        val tp2 = DeviceV2(deviceId = "d2", credentialId = "3party", deviceName = "ENC2")
        whenever(fieldDecryptor.decrypt(tp1)).thenReturn(Error(reason = "bad"))
        whenever(fieldDecryptor.decrypt(tp2)).thenReturn(Error(reason = "bad"))
        whenever(thirdPartyCredentialManager.refresh()).thenReturn(Success(true))

        val result = decryptor.decryptAll(listOf(tp1, tp2))

        assertEquals(listOf("d1", "d2"), result.undecryptable)
        verify(thirdPartyCredentialManager, times(1)).refresh()
    }

    @Test
    fun whenDdgFailsThenNoRefreshAndAddedToLogoutList() {
        val ddg = DeviceV2(deviceId = "d1", credentialId = "ddg", deviceName = "ENC")
        whenever(fieldDecryptor.decrypt(ddg)).thenReturn(Error(reason = "primaryKey rotated?"))

        val result = decryptor.decryptAll(listOf(ddg))

        assertTrue(result.decrypted.isEmpty())
        assertEquals(listOf("d1"), result.undecryptable)
        verify(thirdPartyCredentialManager, never()).refresh()
    }

    @Test
    fun whenMixedDdgFailureAnd3PartyFailureAndRefreshErrorsThenDdgLogoutAnd3PartyFallback() {
        val ddg = DeviceV2(deviceId = "d1", credentialId = "ddg", deviceName = "ENC1")
        val tp = DeviceV2(deviceId = "d2", credentialId = "3party", deviceName = "ENC2")
        whenever(fieldDecryptor.decrypt(ddg)).thenReturn(Error(reason = "ddg bad"))
        whenever(fieldDecryptor.decrypt(tp)).thenReturn(Error(reason = "3p bad"))
        whenever(thirdPartyCredentialManager.refresh()).thenReturn(Error(reason = "network"))

        val result = decryptor.decryptAll(listOf(ddg, tp))

        assertEquals(listOf(DecryptedDevice("d2", FALLBACK_NAME, FALLBACK_TYPE_3PARTY)), result.decrypted)
        assertEquals(listOf("d1"), result.undecryptable)
    }

    @Test
    fun whenEntryHasNoDeviceIdAndFailsThenDroppedFromBothLists() {
        // No deviceId → can't render and can't logout. Silently dropped.
        val orphan = DeviceV2(deviceId = null, credentialId = "ddg", deviceName = "ENC")
        whenever(fieldDecryptor.decrypt(orphan)).thenReturn(Error(reason = "no id"))

        val result = decryptor.decryptAll(listOf(orphan))

        assertTrue(result.decrypted.isEmpty())
        assertTrue(result.undecryptable.isEmpty())
    }

    @Test
    fun whenMixedListWithOne3PartyFailureThenRefreshAndRetryAllEntries() {
        val ddgOk = DeviceV2(deviceId = "d1", credentialId = "ddg", deviceName = "n1")
        val tpFail = DeviceV2(deviceId = "d2", credentialId = "3party", deviceName = "n2")
        val tpOk = DeviceV2(deviceId = "d3", credentialId = "3party", deviceName = "n3")
        whenever(fieldDecryptor.decrypt(ddgOk)).thenReturn(Success(DecryptedDevice("d1", "Pixel", "phone")))
        whenever(fieldDecryptor.decrypt(tpFail))
            .thenReturn(Error(reason = "stale"))
            .thenReturn(Success(DecryptedDevice("d2", "Chrome", "Browser")))
        whenever(fieldDecryptor.decrypt(tpOk)).thenReturn(Success(DecryptedDevice("d3", "Edge", "Browser")))
        whenever(thirdPartyCredentialManager.refresh()).thenReturn(Success(true))

        val result = decryptor.decryptAll(listOf(ddgOk, tpFail, tpOk))

        assertEquals(3, result.decrypted.size)
        assertTrue(result.undecryptable.isEmpty())
        verify(thirdPartyCredentialManager, times(1)).refresh()
        verify(fieldDecryptor, times(2)).decrypt(eq(ddgOk))
        verify(fieldDecryptor, times(2)).decrypt(eq(tpFail))
        verify(fieldDecryptor, times(2)).decrypt(eq(tpOk))
    }

    @Test
    fun whenOnlyDdgEntriesAreInTheListThenNoRefreshEvenIfTheyFail() {
        val ddg1 = DeviceV2(deviceId = "d1", credentialId = "ddg", deviceName = "n")
        val ddg2 = DeviceV2(deviceId = "d2", credentialId = null, deviceName = "n")
        whenever(fieldDecryptor.decrypt(any())).thenReturn(Error(reason = "bad"))

        val result = decryptor.decryptAll(listOf(ddg1, ddg2))

        assertEquals(listOf("d1", "d2"), result.undecryptable)
        verify(thirdPartyCredentialManager, never()).refresh()
    }
}
