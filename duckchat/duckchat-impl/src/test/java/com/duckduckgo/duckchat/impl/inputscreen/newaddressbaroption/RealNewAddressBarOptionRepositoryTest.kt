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

package com.duckduckgo.duckchat.impl.inputscreen.newaddressbaroption

import com.duckduckgo.common.test.CoroutineTestRule
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealNewAddressBarOptionRepositoryTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private var newAddressBarOptionDataStoreMock: NewAddressBarOptionDataStore = mock()

    private lateinit var testee: RealNewAddressBarOptionRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        testee = RealNewAddressBarOptionRepository(
            newAddressBarOptionDataStore = newAddressBarOptionDataStoreMock,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun `when setAsShown is called then dataStore setAsShown is called`() = runTest {
        testee.setAsShown()

        verify(newAddressBarOptionDataStoreMock).setAsShown()
    }

    @Test
    fun `when wasShown is called and dataStore wasShown is true then return true`() = runTest {
        whenever(newAddressBarOptionDataStoreMock.wasShown()).thenReturn(true)

        assertTrue(testee.wasShown())
        verify(newAddressBarOptionDataStoreMock).wasShown()
    }

    @Test
    fun `when wasShown is called and dataStore wasShown is false then return false`() = runTest {
        whenever(newAddressBarOptionDataStoreMock.wasShown()).thenReturn(false)

        assertFalse(testee.wasShown())
        verify(newAddressBarOptionDataStoreMock).wasShown()
    }

    @Test
    fun `when setAsValidated is called then dataStore setAsValidated is called`() = runTest {
        testee.setAsValidated()

        verify(newAddressBarOptionDataStoreMock).setAsValidated()
    }

    @Test
    fun `when wasValidated is called and dataStore wasValidated is true then return true`() = runTest {
        whenever(newAddressBarOptionDataStoreMock.wasValidated()).thenReturn(true)

        assertTrue(testee.wasValidated())
        verify(newAddressBarOptionDataStoreMock).wasValidated()
    }

    @Test
    fun `when wasValidated is called and dataStore wasValidated is false then return false`() = runTest {
        whenever(newAddressBarOptionDataStoreMock.wasValidated()).thenReturn(false)

        assertFalse(testee.wasValidated())
        verify(newAddressBarOptionDataStoreMock).wasValidated()
    }
}
