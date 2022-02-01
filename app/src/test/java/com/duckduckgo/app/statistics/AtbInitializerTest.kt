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

package com.duckduckgo.app.statistics

import com.duckduckgo.app.referral.StubAppReferrerFoundStateListener
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

@ExperimentalCoroutinesApi
class AtbInitializerTest {

    private lateinit var testee: AtbInitializer

    private val statisticsDataStore: StatisticsDataStore = mock()
    private val statisticsUpdater: StatisticsUpdater = mock()
    private lateinit var appReferrerStateListener: AtbInitializerListener

    @Test
    fun whenReferrerInformationInstantlyAvailableThenAtbInitialized() = runTest {
        whenever(statisticsDataStore.hasInstallationStatistics).thenReturn(false)
        appReferrerStateListener = StubAppReferrerFoundStateListener(referrer = "xx")
        testee = AtbInitializer(TestScope(), statisticsDataStore, statisticsUpdater, setOf(appReferrerStateListener))

        testee.initialize()

        verify(statisticsUpdater).initializeAtb()
    }

    @Test
    fun whenReferrerInformationQuicklyAvailableThenAtbInitialized() = runTest {
        whenever(statisticsDataStore.hasInstallationStatistics).thenReturn(false)
        appReferrerStateListener = StubAppReferrerFoundStateListener(referrer = "xx", mockDelayMs = 1000L)
        testee = AtbInitializer(TestScope(), statisticsDataStore, statisticsUpdater, setOf(appReferrerStateListener))

        testee.initialize()

        verify(statisticsUpdater).initializeAtb()
    }

    @Test
    fun whenReferrerInformationTimesOutThenAtbInitialized() = runTest {
        whenever(statisticsDataStore.hasInstallationStatistics).thenReturn(false)
        appReferrerStateListener = StubAppReferrerFoundStateListener(referrer = "xx", mockDelayMs = Long.MAX_VALUE)
        testee = AtbInitializer(TestScope(), statisticsDataStore, statisticsUpdater, setOf(appReferrerStateListener))

        testee.initialize()

        verify(statisticsUpdater).initializeAtb()
    }

    @Test
    fun whenAlreadyInitializedThenRefreshCalled() = runTest {
        configureAlreadyInitialized()
        testee = AtbInitializer(TestScope(), statisticsDataStore, statisticsUpdater, setOf(appReferrerStateListener))

        testee.initialize()
        verify(statisticsUpdater).refreshAppRetentionAtb()
    }

    private fun configureAlreadyInitialized() {
        whenever(statisticsDataStore.hasInstallationStatistics).thenReturn(true)
        appReferrerStateListener = StubAppReferrerFoundStateListener(referrer = "xx")
    }
}
