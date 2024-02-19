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

package com.duckduckgo.fingerprintprotection.store.features.fingerprintinghardware

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.fingerprintprotection.store.FingerprintProtectionDatabase
import com.duckduckgo.fingerprintprotection.store.FingerprintingHardwareEntity
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealFingerprintingHardwareRepositoryTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealFingerprintingHardwareRepository

    private val mockDatabase: FingerprintProtectionDatabase = mock()
    private val mockFingerprintingHardwareDao: FingerprintingHardwareDao = mock()

    @Before
    fun before() {
        whenever(mockFingerprintingHardwareDao.get()).thenReturn(null)
        whenever(mockDatabase.fingerprintingHardwareDao()).thenReturn(mockFingerprintingHardwareDao)
    }

    @Test
    fun whenInitializedAndDoesNotHaveStoredValueThenLoadEmptyJsonToMemory() =
        runTest {
            testee =
                RealFingerprintingHardwareRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    true,
                )

            verify(mockFingerprintingHardwareDao).get()
            assertEquals("{}", testee.fingerprintingHardwareEntity.json)
        }

    @Test
    fun whenInitializedAndHasStoredValueThenLoadStoredJsonToMemory() =
        runTest {
            whenever(mockFingerprintingHardwareDao.get()).thenReturn(fingerprintingHardwareEntity)
            testee =
                RealFingerprintingHardwareRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    true,
                )

            verify(mockFingerprintingHardwareDao).get()
            assertEquals(fingerprintingHardwareEntity.json, testee.fingerprintingHardwareEntity.json)
        }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() =
        runTest {
            testee =
                RealFingerprintingHardwareRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    true,
                )

            testee.updateAll(fingerprintingHardwareEntity)

            verify(mockFingerprintingHardwareDao).updateAll(fingerprintingHardwareEntity)
        }

    companion object {
        val fingerprintingHardwareEntity = FingerprintingHardwareEntity(json = "{\"key\":\"value\"}")
    }
}
