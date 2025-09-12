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
import com.duckduckgo.common.utils.DispatcherProvider
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

    @Mock
    private var dispatcherProviderMock: DispatcherProvider = mock()

    private lateinit var testee: RealNewAddressBarOptionRepository

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        testee = RealNewAddressBarOptionRepository(
            newAddressBarOptionDataStore = newAddressBarOptionDataStoreMock,
            dispatchers = dispatcherProviderMock,
        )
    }

    @Test
    fun `when markAsShown is called then dataStore markAsShown is called`() = runTest {
        testee.markAsShown()

        verify(newAddressBarOptionDataStoreMock).markAsShown()
    }

    @Test
    fun `when hasBeenShown is called and dataStore hasBeenShown is true then return true`() = runTest {
        whenever(newAddressBarOptionDataStoreMock.hasBeenShown()).thenReturn(true)

        assertTrue(testee.hasBeenShown())
        verify(newAddressBarOptionDataStoreMock).hasBeenShown()
    }

    @Test
    fun `when hasBeenShown is called and dataStore hasBeenShown is false then return false`() = runTest {
        whenever(newAddressBarOptionDataStoreMock.hasBeenShown()).thenReturn(false)

        assertFalse(testee.hasBeenShown())
        verify(newAddressBarOptionDataStoreMock).hasBeenShown()
    }

    @Test
    fun `when markAsChecked is called then dataStore markAsChecked is called`() = runTest {
        testee.markAsChecked()

        verify(newAddressBarOptionDataStoreMock).markAsChecked()
    }

    @Test
    fun `when hasBeenChecked is called and dataStore hasBeenChecked is true then return true`() = runTest {
        whenever(newAddressBarOptionDataStoreMock.hasBeenChecked()).thenReturn(true)

        assertTrue(testee.hasBeenChecked())
        verify(newAddressBarOptionDataStoreMock).hasBeenChecked()
    }

    @Test
    fun `when hasBeenChecked is called and dataStore hasBeenChecked is false then return false`() = runTest {
        whenever(newAddressBarOptionDataStoreMock.hasBeenChecked()).thenReturn(false)

        assertFalse(testee.hasBeenChecked())
        verify(newAddressBarOptionDataStoreMock).hasBeenChecked()
    }
}
