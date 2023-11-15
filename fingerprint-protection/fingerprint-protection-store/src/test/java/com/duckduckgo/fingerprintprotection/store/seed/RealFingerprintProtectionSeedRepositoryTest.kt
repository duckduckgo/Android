/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.fingerprintprotection.store.seed

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.fingerprintprotection.store.FingerprintProtectionDatabase
import com.duckduckgo.fingerprintprotection.store.FingerprintProtectionSeedEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class RealFingerprintProtectionSeedRepositoryTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealFingerprintProtectionSeedRepository

    private val mockDatabase: FingerprintProtectionDatabase = mock()
    private val mockFingerprintProtectionSeedDao: FingerprintProtectionSeedDao = mock()

    @Before
    fun before() {
        whenever(mockFingerprintProtectionSeedDao.get()).thenReturn(null)
        whenever(mockDatabase.fingerprintProtectionSeedDao()).thenReturn(mockFingerprintProtectionSeedDao)
    }

    @Test
    fun whenInitializedThenStoreNewSeedAndLoadToMemory() =
        runTest {
            whenever(mockFingerprintProtectionSeedDao.get()).thenReturn(FingerprintProtectionSeedEntity(seed = "1234"))
            testee =
                RealFingerprintProtectionSeedRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                )
            assertEquals("1234", testee.fingerprintProtectionSeedEntity.seed)

            whenever(mockFingerprintProtectionSeedDao.get()).thenReturn(FingerprintProtectionSeedEntity(seed = "5678"))
            testee =
                RealFingerprintProtectionSeedRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                )
            assertEquals("5678", testee.fingerprintProtectionSeedEntity.seed)

            verify(mockFingerprintProtectionSeedDao, times(2)).get()

            val captor = argumentCaptor<FingerprintProtectionSeedEntity>()
            val captor2 = argumentCaptor<FingerprintProtectionSeedEntity>()

            mockFingerprintProtectionSeedDao.inOrder {
                verify().updateAll(captor.capture())
                verify().updateAll(captor2.capture())
            }
            val seed = captor.firstValue.seed
            val seed2 = captor2.firstValue.seed

            assertNotEquals(seed, seed2)
        }

    @Test
    fun whenStoreNewSeedThenStoreNewSeedAndLoadToMemory() =
        runTest {
            whenever(mockFingerprintProtectionSeedDao.get()).thenReturn(FingerprintProtectionSeedEntity(seed = "1234"))
            testee =
                RealFingerprintProtectionSeedRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                )
            testee.storeNewSeed()

            verify(mockFingerprintProtectionSeedDao, times(2)).get()

            val captor = argumentCaptor<FingerprintProtectionSeedEntity>()
            val captor2 = argumentCaptor<FingerprintProtectionSeedEntity>()

            mockFingerprintProtectionSeedDao.inOrder {
                verify().updateAll(captor.capture())
                verify().updateAll(captor2.capture())
            }
            val seed = captor.firstValue.seed
            val seed2 = captor2.firstValue.seed

            assertNotEquals(seed, seed2)
        }
}
