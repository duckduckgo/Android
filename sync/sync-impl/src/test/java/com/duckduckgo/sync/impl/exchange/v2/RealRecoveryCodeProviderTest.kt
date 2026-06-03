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

package com.duckduckgo.sync.impl.exchange.v2

import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.store.ScopedPassword
import com.duckduckgo.sync.store.SyncStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Base64

class RealRecoveryCodeProviderTest {

    private val syncAccountRepository: SyncAccountRepository = mock()
    private val syncStore: SyncStore = mock()
    private val provider = RealRecoveryCodeProvider(syncAccountRepository, syncStore)

    // ---- getDdgRecoveryCode ----

    @Test fun `getDdgRecoveryCode converts v1 shape to v2 with cid=ddg`() {
        val v1Json = """{"recovery":{"primary_key":"pk-abc","user_id":"user-123"}}"""
        val v1B64 = Base64.getUrlEncoder().withoutPadding().encodeToString(v1Json.toByteArray())
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(
            Result.Success(SyncAccountRepository.AuthCode(qrCode = v1B64, rawCode = v1B64)),
        )

        val result = provider.getDdgRecoveryCode()

        assertTrue(result is Result.Success)
        val v2 = decodeRecovery((result as Result.Success).data)
        assertEquals("user-123", v2.getString("user_id"))
        assertEquals("pk-abc", v2.getString("secret"))
        assertEquals("ddg", v2.getString("cid"))
        assertEquals("2.0", v2.getString("v"))
    }

    @Test fun `getDdgRecoveryCode bubbles up repository errors`() {
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Error(reason = "Not signed in"))

        val result = provider.getDdgRecoveryCode()

        assertTrue(result is Result.Error)
        assertEquals("Not signed in", (result as Result.Error).reason)
    }

    @Test fun `getDdgRecoveryCode reports a conversion error when v1 shape is malformed`() {
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(
            Result.Success(SyncAccountRepository.AuthCode(qrCode = "not-b64-or-json", rawCode = "not-b64-or-json")),
        )

        val result = provider.getDdgRecoveryCode()

        assertTrue(result is Result.Error)
        assertTrue(
            "expected conversion error, got: ${(result as Result.Error).reason}",
            result.reason.startsWith("Failed to convert ddg recovery code to v2.0"),
        )
    }

    // ---- getThirdPartyRecoveryCode ----

    @Test fun `getThirdPartyRecoveryCode returns raw rawCode untouched`() {
        // Repo already emits v2.0 shape per [ThirdPartyCredentialManager.getRecoveryCode] —
        // the provider must not re-wrap or re-encode.
        val already3p = "already-v2-encoded-b64"
        whenever(syncAccountRepository.getThirdPartyRecoveryCode()).thenReturn(
            Result.Success(SyncAccountRepository.AuthCode(qrCode = "different-qr-form", rawCode = already3p)),
        )

        val result = provider.getThirdPartyRecoveryCode()

        assertTrue(result is Result.Success)
        assertEquals(already3p, (result as Result.Success).data)
    }

    @Test fun `getThirdPartyRecoveryCode bubbles up repository errors`() {
        whenever(syncAccountRepository.getThirdPartyRecoveryCode()).thenReturn(
            Result.Error(reason = "No 3party credential"),
        )

        val result = provider.getThirdPartyRecoveryCode()

        assertTrue(result is Result.Error)
        assertEquals("No 3party credential", (result as Result.Error).reason)
    }

    // ---- createDdgAccountIfNeeded (spec: "If host has no account yet, create it first.") ----

    @Test fun `createDdgAccountIfNeeded is a no-op when already signed in`() {
        whenever(syncStore.userId).thenReturn("existing-user")

        val result = provider.createDdgAccountIfNeeded()

        assertTrue(result is Result.Success)
        verify(syncAccountRepository, org.mockito.kotlin.never()).createAccount()
    }

    @Test fun `createDdgAccountIfNeeded calls createAccount when no user id present`() {
        whenever(syncStore.userId).thenReturn(null)
        whenever(syncAccountRepository.createAccount()).thenReturn(Result.Success(true))

        val result = provider.createDdgAccountIfNeeded()

        assertTrue(result is Result.Success)
        verify(syncAccountRepository).createAccount()
    }

    @Test fun `createDdgAccountIfNeeded propagates repository error so caller can abort`() {
        whenever(syncStore.userId).thenReturn(null)
        whenever(syncAccountRepository.createAccount()).thenReturn(Result.Error(reason = "BE down"))

        val result = provider.createDdgAccountIfNeeded()

        assertTrue(result is Result.Error)
        assertEquals("BE down", (result as Result.Error).reason)
    }

    // ---- createThirdPartyCredentialIfNeeded (spec: "extend the account" for 3party peer) ----

    @Test fun `createThirdPartyCredentialIfNeeded is a no-op when scopedPassword already set locally`() {
        // ScopedPassword is a `value class` so Mockito can't mock it — use a real instance.
        whenever(syncStore.scopedPassword).thenReturn(ScopedPassword("existing"))

        val result = provider.createThirdPartyCredentialIfNeeded()

        assertTrue(result is Result.Success)
        verify(syncAccountRepository, org.mockito.kotlin.never()).createThirdPartyCredential()
    }

    @Test fun `createThirdPartyCredentialIfNeeded extends the account when no scopedPassword locally`() {
        whenever(syncStore.scopedPassword).thenReturn(null)
        whenever(syncAccountRepository.createThirdPartyCredential()).thenReturn(Result.Success(true))

        val result = provider.createThirdPartyCredentialIfNeeded()

        assertTrue(result is Result.Success)
        verify(syncAccountRepository).createThirdPartyCredential()
    }

    @Test fun `createThirdPartyCredentialIfNeeded propagates repository error`() {
        whenever(syncStore.scopedPassword).thenReturn(null)
        whenever(syncAccountRepository.createThirdPartyCredential())
            .thenReturn(Result.Error(reason = "extend failed"))

        val result = provider.createThirdPartyCredentialIfNeeded()

        assertTrue(result is Result.Error)
        assertEquals("extend failed", (result as Result.Error).reason)
    }

    private fun decodeRecovery(b64: String): JSONObject {
        val bytes = Base64.getUrlDecoder().decode(b64)
        return JSONObject(String(bytes, Charsets.UTF_8)).getJSONObject("recovery")
    }
}
