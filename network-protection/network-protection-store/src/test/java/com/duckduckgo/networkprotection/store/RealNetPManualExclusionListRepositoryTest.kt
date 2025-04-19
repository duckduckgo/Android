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

package com.duckduckgo.networkprotection.store

import com.duckduckgo.networkprotection.store.db.NetPExclusionListDao
import com.duckduckgo.networkprotection.store.db.NetPManuallyExcludedApp
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealNetPManualExclusionListRepositoryTest {
    @Mock
    private lateinit var exclusionListDao: NetPExclusionListDao
    private lateinit var testee: RealNetPManualExclusionListRepository

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(exclusionListDao.getManualAppExclusionList()).thenReturn(MANUAL_EXCLUSION_LIST)
        testee = RealNetPManualExclusionListRepository(exclusionListDao)
    }

    @Test
    fun whenGetManualAppExclusionListThenDelegateToNetPExclusionListDao() {
        testee.getManualAppExclusionList()

        verify(exclusionListDao).getManualAppExclusionList()
    }

    @Test
    fun whenGetManualAppExclusionListFlowThenDelegateToNetPExclusionListDao() {
        testee.getManualAppExclusionListFlow()

        verify(exclusionListDao).getManualAppExclusionListFlow()
    }

    @Test
    fun whenManuallyExcludeAppThenDelegateToNetPExclusionListDao() {
        testee.manuallyExcludeApp("test")

        verify(exclusionListDao).insertIntoManualAppExclusionList(NetPManuallyExcludedApp(packageId = "test", isProtected = false))
    }

    @Test
    fun whenManuallyExcludeAppsThenDelegateToNetPExclusionListDao() {
        testee.manuallyExcludeApps(
            listOf(
                "test1",
                "test2",
                "test3",
            ),
        )

        verify(exclusionListDao).insertIntoManualAppExclusionList(
            listOf(
                NetPManuallyExcludedApp(packageId = "test1", isProtected = false),
                NetPManuallyExcludedApp(packageId = "test2", isProtected = false),
                NetPManuallyExcludedApp(packageId = "test3", isProtected = false),
            ),
        )
    }

    @Test
    fun whenManuallyEnableAppThenDelegateToNetPExclusionListDao() {
        testee.manuallyEnableApp("test")

        verify(exclusionListDao).insertIntoManualAppExclusionList(NetPManuallyExcludedApp(packageId = "test", isProtected = true))
    }

    @Test
    fun whenRestoreDefaultProtectedListThenDelegateToNetPExclusionListDao() {
        testee.restoreDefaultProtectedList()

        verify(exclusionListDao).deleteManualAppExclusionList()
    }

    companion object {
        private val MANUAL_EXCLUSION_LIST = listOf(
            NetPManuallyExcludedApp("com.example.app1", true),
            NetPManuallyExcludedApp("com.example.app2", false),
            NetPManuallyExcludedApp("com.example.app3", false),
        )
    }
}
