/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.configuration

import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealDeviceKeysTest {
    @Mock
    private lateinit var networkProtectionRepository: NetworkProtectionRepository

    @Mock
    private lateinit var keyPairGenerator: KeyPairGenerator

    private lateinit var testee: RealDeviceKeys

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealDeviceKeys(networkProtectionRepository, keyPairGenerator)
    }

    @Test
    fun whenNoPrivateKeyInRepositoryThenGeneratePrivateKeyAndStoreIt() {
        val expectedPrivateKey = "testprivatekey123"
        whenever(networkProtectionRepository.privateKey).thenReturn(null)
        whenever(keyPairGenerator.generatePrivateKey()).thenReturn(expectedPrivateKey)

        assertEquals(expectedPrivateKey, testee.privateKey)
        verify(networkProtectionRepository).privateKey = expectedPrivateKey
    }

    @Test
    fun whenEmptyPrivateKeyInRepositoryThenGeneratePrivateKeyAndStoreIt() {
        val expectedPrivateKey = "testprivatekey123"
        whenever(networkProtectionRepository.privateKey).thenReturn("")
        whenever(keyPairGenerator.generatePrivateKey()).thenReturn(expectedPrivateKey)

        assertEquals(expectedPrivateKey, testee.privateKey)
        verify(networkProtectionRepository).privateKey = expectedPrivateKey
    }

    @Test
    fun whenExistingPrivateKeyInRepositoryThenReturnPrivateKey() {
        val expectedPrivateKey = "testprivatekey123"
        whenever(networkProtectionRepository.privateKey).thenReturn(expectedPrivateKey)

        assertEquals(expectedPrivateKey, testee.privateKey)
    }

    @Test
    fun whenExistingPrivateKeyThenReturnPublicKeyUsingStoredPrivateKey() {
        val expectedPrivateKey = "testprivatekey123"
        val expectedPublicKey = "testpublickey123"
        whenever(networkProtectionRepository.privateKey).thenReturn(expectedPrivateKey)
        whenever(keyPairGenerator.generatePublicKey(expectedPrivateKey)).thenReturn(expectedPublicKey)

        assertEquals(expectedPublicKey, testee.publicKey)
    }

    @Test
    fun whenNoPrivateKeyThenReturnPublicKeyUsingGeneratedPrivateKey() {
        val expectedPrivateKey = "testprivatekey123"
        val expectedPublicKey = "testpublickey123"
        whenever(networkProtectionRepository.privateKey).thenReturn(null)
        whenever(keyPairGenerator.generatePrivateKey()).thenReturn(expectedPrivateKey)
        whenever(keyPairGenerator.generatePublicKey(expectedPrivateKey)).thenReturn(expectedPublicKey)

        assertEquals(expectedPublicKey, testee.publicKey)
    }
}
