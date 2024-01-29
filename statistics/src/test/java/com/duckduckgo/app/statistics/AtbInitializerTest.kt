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

import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AtbInitializerTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var testee: AtbInitializer

    private val statisticsDataStore: StatisticsDataStore = mock()
    private val statisticsUpdater: StatisticsUpdater = mock()
    private var atbInitializerListener = FakeAtbInitializerListener()

    @Test
    fun whenReferrerInformationInstantlyAvailableThenAtbInitialized() = runTest {
        configureNeverInitialized()

        testee.onPrivacyConfigDownloaded()

        verify(statisticsUpdater).initializeAtb()
    }

    @Test
    fun whenReferrerInformationQuicklyAvailableThenAtbInitialized() = runTest {
        whenever(statisticsDataStore.hasInstallationStatistics).thenReturn(false)
        atbInitializerListener.delay = 1.seconds
        testee = AtbInitializer(
            coroutineRule.testScope,
            statisticsDataStore,
            statisticsUpdater,
            setOf(atbInitializerListener),
            coroutineRule.testDispatcherProvider,
        )

        testee.onPrivacyConfigDownloaded()

        verify(statisticsUpdater).initializeAtb()
    }

    @Test
    fun whenReferrerInformationTimesOutThenRefreshAtbNotCalled() = runTest {
        whenever(statisticsDataStore.hasInstallationStatistics).thenReturn(false)
        atbInitializerListener.delay = Duration.INFINITE
        testee = AtbInitializer(
            coroutineRule.testScope,
            statisticsDataStore,
            statisticsUpdater,
            setOf(atbInitializerListener),
            coroutineRule.testDispatcherProvider,
        )

        testee.initialize()

        verify(statisticsUpdater, never()).initializeAtb()
    }

    @Test
    fun whenAlreadyInitializedThenRefreshCalled() = runTest {
        configureAlreadyInitialized()

        testee.initialize()

        verify(statisticsUpdater).refreshAppRetentionAtb()
    }

    @Test
    fun givenHasInstallationStatisticsWhenOnPrivacyConfigDownloadedThenAtbInitializedNeverCalled() = runTest {
        configureAlreadyInitialized()

        testee.onPrivacyConfigDownloaded()

        verify(statisticsUpdater, never()).initializeAtb()
    }

    @Test
    fun givenNeverInstallationStatisticsWhenOnPrivacyConfigDownloadedThenAtbInitialized() = runTest {
        configureNeverInitialized()

        testee.onPrivacyConfigDownloaded()

        verify(statisticsUpdater).initializeAtb()
    }

    private fun configureNeverInitialized() {
        whenever(statisticsDataStore.hasInstallationStatistics).thenReturn(false)
        testee = AtbInitializer(
            coroutineRule.testScope,
            statisticsDataStore,
            statisticsUpdater,
            setOf(atbInitializerListener),
            coroutineRule.testDispatcherProvider,
        )
    }

    private fun configureAlreadyInitialized() {
        whenever(statisticsDataStore.hasInstallationStatistics).thenReturn(true)
        testee = AtbInitializer(
            coroutineRule.testScope,
            statisticsDataStore,
            statisticsUpdater,
            setOf(atbInitializerListener),
            coroutineRule.testDispatcherProvider,
        )
    }
}

class FakeAtbInitializerListener : AtbInitializerListener {

    var delay: Duration = Duration.ZERO

    override suspend fun beforeAtbInit() {
        delay(delay)
    }

    override fun beforeAtbInitTimeoutMillis(): Long = delay.inWholeMilliseconds
}
