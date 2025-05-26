/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.com.duckduckgo.autofill.store

import com.duckduckgo.autofill.store.RealSecureStorageKeyRepository
import com.duckduckgo.autofill.store.keys.SecureStorageKeyStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealSecureStorageKeyRepositoryTest {
    private val keyStore: SecureStorageKeyStore = mock()
    private lateinit var testee: RealSecureStorageKeyRepository
    private val testValue = "TestValue".toByteArray()

    @Before
    fun setUp() {
        testee = RealSecureStorageKeyRepository(keyStore)
    }

    @Test
    fun whenPasswordIsSetThenUpdateKeyForPasswordInKeyStore() = runTest {
        testee.setPassword(testValue)

        verify(keyStore).updateKey("KEY_GENERATED_PASSWORD", testValue)
    }

    @Test
    fun whenGettingPasswordThenGetKeyForPasswordInKeyStore() = runTest {
        whenever(keyStore.getKey("KEY_GENERATED_PASSWORD")).thenReturn(testValue)

        assertEquals(testValue, testee.getPassword())
    }

    @Test
    fun whenL1KeyIsSetThenUpdateKeyForL1KeyInKeyStore() = runTest {
        testee.setL1Key(testValue)

        verify(keyStore).updateKey("KEY_L1KEY", testValue)
    }

    @Test
    fun whenGettingL1KeyThenGetKeyForL1KeyInKeyStore() = runTest {
        whenever(keyStore.getKey("KEY_L1KEY")).thenReturn(testValue)

        assertEquals(testValue, testee.getL1Key())
    }

    @Test
    fun whenPasswordSaltIsSetThenUpdateKeyForPasswordSaltInKeyStore() = runTest {
        testee.setPasswordSalt(testValue)

        verify(keyStore).updateKey("KEY_PASSWORD_SALT", testValue)
    }

    @Test
    fun whenGettingPasswordSaltThenGetKeyForPasswordSaltKeyInKeyStore() = runTest {
        whenever(keyStore.getKey("KEY_PASSWORD_SALT")).thenReturn(testValue)

        assertEquals(testValue, testee.getPasswordSalt())
    }

    @Test
    fun whenEncryptedL2KeyIsSetThenUpdateKeyForEncryptedL2KeyInKeyStore() = runTest {
        testee.setEncryptedL2Key(testValue)

        verify(keyStore).updateKey("KEY_ENCRYPTED_L2KEY", testValue)
    }

    @Test
    fun whenGettingEncryptedL2KeyThenGetKeyForEncryptedL2KeyInKeyStore() = runTest {
        whenever(keyStore.getKey("KEY_ENCRYPTED_L2KEY")).thenReturn(testValue)

        assertEquals(testValue, testee.getEncryptedL2Key())
    }

    @Test
    fun whenEncryptedL2KeyIVIsSetThenUpdateKeyForEncryptedL2KeyIVInKeyStore() = runTest {
        testee.setEncryptedL2KeyIV(testValue)

        verify(keyStore).updateKey("KEY_ENCRYPTED_L2KEY_IV", testValue)
    }

    @Test
    fun whenGettingEncryptedL2KeyIVThenGetKeyForEncryptedL2KeyIVInKeyStore() = runTest {
        whenever(keyStore.getKey("KEY_ENCRYPTED_L2KEY_IV")).thenReturn(testValue)

        assertEquals(testValue, testee.getEncryptedL2KeyIV())
    }
}
