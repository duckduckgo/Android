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
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.store.AccountInfoPublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SignupAccountInfoBuilderTest {

    private val accountInfoKeyManager: AccountInfoKeyManager = mock()
    private val deviceInfoEncryptor: DeviceInfoEncryptor = mock()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)

    private lateinit var builder: SignupAccountInfoBuilder

    private val secretKey = "accountSecretKey"

    @Before
    fun before() {
        builder = RealSignupAccountInfoBuilder(syncFeature, accountInfoKeyManager, deviceInfoEncryptor)
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = true))
    }

    @Test
    fun whenWriteFeatureOffThenNullAndNoMint() {
        syncFeature.canWriteUnifiedDeviceList().setRawStoredState(State(enable = false))

        assertNull(builder.build(secretKey, "My Phone", "phone"))
        verify(accountInfoKeyManager, never()).mintUnregistered(any())
    }

    @Test
    fun whenMintFailsThenNullAndNoEncrypt() {
        whenever(accountInfoKeyManager.mintUnregistered(secretKey)).thenReturn(Error(reason = "mint boom"))

        assertNull(builder.build(secretKey, "My Phone", "phone"))
        verify(deviceInfoEncryptor, never()).encrypt(any(), any(), any())
    }

    @Test
    fun whenEncryptFailsThenNull() {
        whenever(accountInfoKeyManager.mintUnregistered(secretKey)).thenReturn(Success(mintedKey()))
        whenever(deviceInfoEncryptor.encrypt(any(), any(), any())).thenReturn(Error(reason = "encrypt boom"))

        assertNull(builder.build(secretKey, "My Phone", "phone"))
    }

    @Test
    fun whenMintedAndEncryptedThenReturnsAdditionsWithMatchingKid() {
        whenever(accountInfoKeyManager.mintUnregistered(secretKey)).thenReturn(Success(mintedKey()))
        whenever(deviceInfoEncryptor.encrypt(any(), any(), any())).thenReturn(Success("deviceInfoJwe"))

        val result = builder.build(secretKey, "My Phone", "phone")

        val expectedPublicKey = AccountInfoPublicKey(keyId = "kid-1", modulus = "n", exponent = "AQAB")
        assertEquals("deviceInfoJwe", result?.deviceInfo)
        assertEquals(1, result?.keys?.size)
        assertEquals("kid-1", result?.keys?.first()?.kid)
        assertEquals(CREDENTIAL_ID_DDG, result?.keys?.first()?.encryptedWith)
        assertEquals(expectedPublicKey, result?.publicKey)
        // device_info must be encrypted under the minted key so its kid matches the registered key entry.
        verify(deviceInfoEncryptor).encrypt(eq("My Phone"), eq("phone"), eq(expectedPublicKey))
    }

    private fun mintedKey() = MintedProtectedKey(
        entry = ProtectedKeyEntry(
            kid = "kid-1",
            purpose = SYNC_PURPOSE_ACCOUNT_INFO,
            encryptedWith = CREDENTIAL_ID_DDG,
            encryptedPrivateKey = "ddg-wrapped-private-key",
            publicKey = RsaJwk(n = "n", e = "AQAB"),
        ),
        rawPrivateKeyBytes = "raw".toByteArray(),
    )
}
