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

package com.duckduckgo.fingerprintprotection.store.features.fingerprintingscreensize

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.fingerprintprotection.store.FingerprintProtectionDatabase
import com.duckduckgo.fingerprintprotection.store.FingerprintingScreenSizeEntity
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealFingerprintingScreenSizeRepositoryTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealFingerprintingScreenSizeRepository

    private val mockDatabase: FingerprintProtectionDatabase = mock()
    private val mockFingerprintingScreenSizeDao: FingerprintingScreenSizeDao = mock()

    @Before
    fun before() {
        whenever(mockFingerprintingScreenSizeDao.get()).thenReturn(null)
        whenever(mockDatabase.fingerprintingScreenSizeDao()).thenReturn(mockFingerprintingScreenSizeDao)
    }

    @Test
    fun whenInitializedAndDoesNotHaveStoredValueThenLoadEmptyJsonToMemory() =
        runTest {
            testee =
                RealFingerprintingScreenSizeRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    true,
                )

            verify(mockFingerprintingScreenSizeDao).get()
            assertEquals("{}", testee.fingerprintingScreenSizeEntity.json)
        }

    @Test
    fun whenInitializedAndHasStoredValueThenLoadStoredJsonToMemory() =
        runTest {
            whenever(mockFingerprintingScreenSizeDao.get()).thenReturn(fingerprintingScreenSizeEntity)
            testee =
                RealFingerprintingScreenSizeRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    true,
                )

            verify(mockFingerprintingScreenSizeDao).get()
            assertEquals(fingerprintingScreenSizeEntity.json, testee.fingerprintingScreenSizeEntity.json)
        }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() =
        runTest {
            testee =
                RealFingerprintingScreenSizeRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    true,
                )

            testee.updateAll(fingerprintingScreenSizeEntity)

            verify(mockFingerprintingScreenSizeDao).updateAll(fingerprintingScreenSizeEntity)
        }

    companion object {
        val fingerprintingScreenSizeEntity = FingerprintingScreenSizeEntity(json = "{\"key\":\"value\"}")
    }
}
