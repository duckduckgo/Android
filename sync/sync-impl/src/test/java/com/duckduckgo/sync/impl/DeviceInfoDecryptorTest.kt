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
import com.duckduckgo.sync.impl.crypto.SyncJweCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class DeviceInfoDecryptorTest {

    private val accountInfoPrivateKeyProvider: AccountInfoPrivateKeyProvider = mock()
    private val syncJweCrypto: SyncJweCrypto = mock()
    private val decryptor = RealDeviceInfoDecryptor(accountInfoPrivateKeyProvider, syncJweCrypto)

    @Test
    fun whenBlobDecryptsThenReturnNameAndType() {
        givenPrivateKeyAvailable()
        whenever(syncJweCrypto.jweDecryptRsaOaep("device.info.jwe", "private-key"))
            .thenReturn("""{"name":"My Phone","type":"phone"}""".toByteArray(Charsets.UTF_8))

        val result = openSessionAndDecrypt("device.info.jwe")

        assertEquals(Result.Success(DeviceInfoPayload(name = "My Phone", type = "phone")), result)
    }

    @Test
    fun whenTypeIsEmptyThenReturnNullType() {
        givenPrivateKeyAvailable()
        whenever(syncJweCrypto.jweDecryptRsaOaep(any(), any()))
            .thenReturn("""{"name":"My Phone","type":""}""".toByteArray(Charsets.UTF_8))

        val result = openSessionAndDecrypt("device.info.jwe")

        assertEquals(Result.Success(DeviceInfoPayload(name = "My Phone", type = null)), result)
    }

    @Test
    fun whenPrivateKeyOutcomeIsUnavailableThenSessionPreservesTypedReasonWithoutDecrypting() {
        whenever(accountInfoPrivateKeyProvider.privateKey()).thenReturn(
            AccountInfoPrivateKeyResult.Unavailable(AccountInfoKeyUnavailableReason.NO_WRAP_FOR_OUR_CREDENTIAL),
        )

        val result = decryptor.openSession()

        assertEquals(
            DeviceInfoSessionResult.Unavailable(AccountInfoKeyUnavailableReason.NO_WRAP_FOR_OUR_CREDENTIAL),
            result,
        )
        verify(syncJweCrypto, never()).jweDecryptRsaOaep(any(), any())
    }

    @Test
    fun whenBlobCannotBeDecryptedThenReturnError() {
        givenPrivateKeyAvailable()
        whenever(syncJweCrypto.jweDecryptRsaOaep(eq("device.info.jwe"), any())).thenThrow(RuntimeException("bad tag"))

        assertTrue(openSessionAndDecrypt("device.info.jwe") is Result.Error)
    }

    @Test
    fun whenPayloadIsNotValidJsonThenReturnError() {
        givenPrivateKeyAvailable()
        whenever(syncJweCrypto.jweDecryptRsaOaep(any(), any())).thenReturn("{ malformed".toByteArray(Charsets.UTF_8))

        assertTrue(openSessionAndDecrypt("device.info.jwe") is Result.Error)
    }

    @Test
    fun whenSessionDecryptsManyBlobsThenPrivateKeyFetchedOnce() {
        givenPrivateKeyAvailable()
        whenever(syncJweCrypto.jweDecryptRsaOaep(any(), any()))
            .thenReturn("""{"name":"n","type":"phone"}""".toByteArray(Charsets.UTF_8))

        val session = (decryptor.openSession() as DeviceInfoSessionResult.Available).session
        session.decrypt("a")
        session.decrypt("b")
        session.decrypt("c")

        verify(accountInfoPrivateKeyProvider, times(1)).privateKey()
    }

    private fun givenPrivateKeyAvailable() {
        whenever(accountInfoPrivateKeyProvider.privateKey())
            .thenReturn(AccountInfoPrivateKeyResult.Available("private-key"))
    }

    private fun openSessionAndDecrypt(deviceInfoJwe: String): Result<DeviceInfoPayload> =
        when (val outcome = decryptor.openSession()) {
            is DeviceInfoSessionResult.Available -> outcome.session.decrypt(deviceInfoJwe)
            is DeviceInfoSessionResult.Unavailable -> Result.Error(reason = outcome.reason.name)
        }
}
