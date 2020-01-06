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

import com.duckduckgo.app.referral.AppInstallationReferrerStateListener
import com.duckduckgo.app.referral.ParsedReferrerResult
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test
import org.mockito.stubbing.Answer

@ExperimentalCoroutinesApi
class AtbInitializerTest {

    private lateinit var testee: AtbInitializer

    private val statisticsDataStore: StatisticsDataStore = mock()
    private val statisticsUpdater: StatisticsUpdater = mock()
    private val appReferrerStateListener: AppInstallationReferrerStateListener = mock()

    @Before
    fun setup() {
        testee = AtbInitializer(statisticsDataStore, statisticsUpdater, appReferrerStateListener)
    }

    @Test
    fun whenReferrerInformationInstantlyAvailableThenAtbInitialized() = runBlockingTest {
        whenever(statisticsDataStore.hasInstallationStatistics).thenReturn(false)
        whenever(appReferrerStateListener.waitForReferrerCode()).thenAnswer(referrerAnswer(0))

        testee.initializeAfterReferrerAvailable()

        verify(statisticsUpdater).initializeAtb()
    }

    @Test
    fun whenReferrerInformationQuicklyAvailableThenAtbInitialized() = runBlockingTest {
        whenever(statisticsDataStore.hasInstallationStatistics).thenReturn(false)
        whenever(appReferrerStateListener.waitForReferrerCode()).thenAnswer(referrerAnswer(1000))

        testee.initializeAfterReferrerAvailable()

        verify(statisticsUpdater).initializeAtb()
    }

    @Test
    fun whenReferrerInformationTimesOutThenAtbInitialized() = runBlockingTest {
        whenever(statisticsDataStore.hasInstallationStatistics).thenReturn(false)
        whenever(appReferrerStateListener.waitForReferrerCode()).thenAnswer(referrerAnswer(Long.MAX_VALUE))

        testee.initializeAfterReferrerAvailable()

        verify(statisticsUpdater).initializeAtb()
    }

    @Test
    fun whenAlreadyInitializedThenRefreshCalled() = runBlockingTest {
        configureAlreadyInitialized()
        testee.initializeAfterReferrerAvailable()
        verify(statisticsUpdater).refreshAppRetentionAtb()
    }

    private suspend fun configureAlreadyInitialized() {
        whenever(statisticsDataStore.hasInstallationStatistics).thenReturn(true)
        whenever(appReferrerStateListener.waitForReferrerCode()).thenAnswer(referrerAnswer(0))
    }

    private suspend fun referrerAnswer(delayMs: Long): Answer<ParsedReferrerResult> {
        delay(delayMs)
        return Answer { ParsedReferrerResult.ReferrerFound("") }
    }
}