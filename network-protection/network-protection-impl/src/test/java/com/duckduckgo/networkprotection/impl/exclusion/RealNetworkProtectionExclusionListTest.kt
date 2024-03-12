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

package com.duckduckgo.networkprotection.impl.exclusion

import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.store.NetPExclusionListRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class RealNetworkProtectionExclusionListTest {

    @Mock
    private lateinit var networkProtectionState: NetworkProtectionState

    @Mock
    private lateinit var netPExclusionListRepository: NetPExclusionListRepository

    private lateinit var testee: RealNetworkProtectionExclusionList

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealNetworkProtectionExclusionList(netPExclusionListRepository, networkProtectionState)
    }

    @Test
    fun whenNetpIsEnabledAndAppIsInExcludedPackagesThenReturnIsExcludedTrue() = runTest {
        whenever(netPExclusionListRepository.getExcludedAppPackages()).thenReturn(listOf("com.test.app"))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        assertTrue(testee.isExcluded("com.test.app"))
    }

    @Test
    fun whenNetpIsDisabledAndAppIsInExcludedPackagesThenReturnIsExcludedFalse() = runTest {
        whenever(netPExclusionListRepository.getExcludedAppPackages()).thenReturn(listOf("com.test.app"))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)

        assertFalse(testee.isExcluded("com.test.app"))
    }

    @Test
    fun whenNetpIsEnabledAndAppIsNotInExcludedPackagesThenReturnIsExcludedFalse() = runTest {
        whenever(netPExclusionListRepository.getExcludedAppPackages()).thenReturn(emptyList())
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        assertFalse(testee.isExcluded("com.test.app"))
    }

    @Test
    fun whenNetpIsNotEnabledAndAppIsNotInExcludedPackagesThenReturnIsExcludedFalse() = runTest {
        whenever(netPExclusionListRepository.getExcludedAppPackages()).thenReturn(emptyList())
        whenever(networkProtectionState.isEnabled()).thenReturn(false)

        assertFalse(testee.isExcluded("com.test.app"))
    }
}
