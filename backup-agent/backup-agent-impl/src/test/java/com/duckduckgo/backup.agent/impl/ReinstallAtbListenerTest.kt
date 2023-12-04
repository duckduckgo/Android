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

package com.duckduckgo.backup.agent.impl

import com.duckduckgo.app.backup.agent.impl.ReinstallAtbListener
import com.duckduckgo.app.statistics.model.Atb
import com.duckduckgo.app.statistics.store.BackupDataStore
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class ReinstallAtbListenerTest {

    private lateinit var testee: ReinstallAtbListener

    private val mockStatisticsDataStore: StatisticsDataStore = mock()
    private val mockBackupDataStore: BackupDataStore = mock()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun before() {
        whenever(mockStatisticsDataStore.hasInstallationStatistics).thenReturn(true)
        testee = ReinstallAtbListener(
            mockStatisticsDataStore,
            mockBackupDataStore,
        )
    }

    @Test
    fun givenStatisticsATBPersistedWhenAtbInitialiseCalledIfBackupATBNotExistThenChangeBackupATB() = runTest {
        whenever(mockStatisticsDataStore.atb).thenReturn(Atb("atb"))
        whenever(mockBackupDataStore.atb).thenReturn(null)

        testee.beforeAtbInit()

        verify(mockBackupDataStore).atb = mockStatisticsDataStore.atb
    }

    @Test
    fun givenStatisticsATBPersistedWhenAtbInitialiseCalledIfBackupATBIsDifferentThenChangeBackupATB() = runTest {
        whenever(mockStatisticsDataStore.atb).thenReturn(Atb("atb"))
        whenever(mockBackupDataStore.atb).thenReturn(Atb("oldAtb"))

        testee.beforeAtbInit()

        verify(mockBackupDataStore).atb = mockStatisticsDataStore.atb
    }

    @Test
    fun givenInstallWhenAtbInitialiseCalledIfBackupATBAndStatisticsATBAreDifferentThenBackupATBNotChanged() = runTest {
        whenever(mockStatisticsDataStore.atb).thenReturn(null)
        whenever(mockBackupDataStore.atb).thenReturn(Atb("oldAtb"))
        whenever(mockStatisticsDataStore.hasInstallationStatistics).thenReturn(false)

        testee.beforeAtbInit()

        verify(mockBackupDataStore, never()).atb = mockStatisticsDataStore.atb
    }
}
