/*
 * Copyright (c) 2019 DuckDuckGo
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
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test

@ExperimentalCoroutinesApi
class AtbInitializerTest {

    private lateinit var testee: AtbInitializer

    private val statisticsDataStore: StatisticsDataStore = mock()
    private val statisticsUpdater: StatisticsUpdater = mock()
    private lateinit var appReferrerStateListener: AtbInitializerListener

    @Test
    fun whenReferrerInformationInstantlyAvailableThenAtbInitialized() = runBlockingTest {
        whenever(statisticsDataStore.hasInstallationStatistics).thenReturn(false)
        appReferrerStateListener = StubAppReferrerFoundStateListener(referrer = "xx")
        testee =
            AtbInitializer(
                TestCoroutineScope(),
                statisticsDataStore,
                statisticsUpdater,
                setOf(appReferrerStateListener))

        testee.initialize()

        verify(statisticsUpdater).initializeAtb()
    }

    @Test
    fun whenReferrerInformationQuicklyAvailableThenAtbInitialized() = runBlockingTest {
        whenever(statisticsDataStore.hasInstallationStatistics).thenReturn(false)
        appReferrerStateListener =
            StubAppReferrerFoundStateListener(referrer = "xx", mockDelayMs = 1000L)
        testee =
            AtbInitializer(
                TestCoroutineScope(),
                statisticsDataStore,
                statisticsUpdater,
                setOf(appReferrerStateListener))

        testee.initialize()

        verify(statisticsUpdater).initializeAtb()
    }

    @Test
    fun whenReferrerInformationTimesOutThenAtbInitialized() = runBlockingTest {
        whenever(statisticsDataStore.hasInstallationStatistics).thenReturn(false)
        appReferrerStateListener =
            StubAppReferrerFoundStateListener(referrer = "xx", mockDelayMs = Long.MAX_VALUE)
        testee =
            AtbInitializer(
                TestCoroutineScope(),
                statisticsDataStore,
                statisticsUpdater,
                setOf(appReferrerStateListener))

        testee.initialize()

        verify(statisticsUpdater).initializeAtb()
    }

    @Test
    fun whenAlreadyInitializedThenRefreshCalled() = runBlockingTest {
        configureAlreadyInitialized()
        testee =
            AtbInitializer(
                TestCoroutineScope(),
                statisticsDataStore,
                statisticsUpdater,
                setOf(appReferrerStateListener))

        testee.initialize()
        verify(statisticsUpdater).refreshAppRetentionAtb()
    }

    private fun configureAlreadyInitialized() {
        whenever(statisticsDataStore.hasInstallationStatistics).thenReturn(true)
        appReferrerStateListener = StubAppReferrerFoundStateListener(referrer = "xx")
    }
}
