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

import androidx.lifecycle.testing.TestLifecycleOwner
import com.duckduckgo.app.statistics.api.StatisticsUpdater
import com.duckduckgo.app.statistics.pixels.AtbInitializationPluginPixelSender
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.Duration
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AtbInitializerTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var testee: AtbInitializer

    private val statisticsDataStore: StatisticsDataStore = mock()
    private val statisticsUpdater: StatisticsUpdater = mock()
    private var atbInitializerListener = FakeAtbInitializerListener()
    private val lifecycleOwner = TestLifecycleOwner()

    private val pixelSender: AtbInitializationPluginPixelSender = mock()

    private val listeners = object : PluginPoint<AtbInitializerListener> {
        override fun getPlugins(): Collection<AtbInitializerListener> {
            return setOf(atbInitializerListener)
        }
    }
    private val emptyListeners = object : PluginPoint<AtbInitializerListener> {
        override fun getPlugins(): Collection<AtbInitializerListener> {
            return emptyList()
        }
    }

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
            emptyListeners,
            coroutineRule.testDispatcherProvider,
            pixelSender = pixelSender,
        )

        testee.onPrivacyConfigDownloaded()

        verify(statisticsUpdater).initializeAtb()
    }

    @Test
    fun whenReferrerInformationTimesOutThenInitializeStillCalled() = runTest {
        configureNeverInitialized()
        atbInitializerListener.delay = INFINITE
        atbInitializerListener.timeout = 1_000 // ensure timeout occurs
        testee.onPrivacyConfigDownloaded()
        advanceUntilIdle()

        verify(statisticsUpdater).initializeAtb()
    }

    @Test
    fun whenReferrerInformationTimesOutThenPixelSent() = runTest {
        configureNeverInitialized()
        atbInitializerListener.delay = INFINITE
        atbInitializerListener.timeout = 1_000 // ensure timeout occurs
        testee.onPrivacyConfigDownloaded()
        advanceUntilIdle()

        verify(pixelSender).pluginTimedOut(any())
    }

    @Test
    fun whenReferrerInformationDoesNotTimeOutThenPixelNotSent() = runTest {
        configureNeverInitialized()
        testee.onPrivacyConfigDownloaded()
        advanceUntilIdle()

        verify(pixelSender, never()).pluginTimedOut(any())
    }

    @Test
    fun whenAlreadyInitializedThenRefreshCalled() = runTest {
        configureAlreadyInitialized()

        testee.onResume(lifecycleOwner)

        verify(statisticsUpdater).refreshAppRetentionAtb()
    }

    @Test
    fun givenHasInstallationStatisticsWhenOnPrivacyConfigDownloadedThenAtbInitializedNeverCalled() = runTest {
        configureAlreadyInitialized()

        testee.onPrivacyConfigDownloaded()

        verify(statisticsUpdater, never()).initializeAtb()
    }

    @Test
    fun givenNeverInstallationStatisticsWhenOnPrivacyConfigDownloadedThenAuraExperimentAndAtbInitialized() = runTest {
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
            listeners,
            coroutineRule.testDispatcherProvider,
            pixelSender = pixelSender,
        )
    }

    private fun configureAlreadyInitialized() {
        whenever(statisticsDataStore.hasInstallationStatistics).thenReturn(true)
        testee = AtbInitializer(
            coroutineRule.testScope,
            statisticsDataStore,
            statisticsUpdater,
            listeners,
            coroutineRule.testDispatcherProvider,
            pixelSender = pixelSender,
        )
    }
}

class FakeAtbInitializerListener : AtbInitializerListener {

    var delay: Duration = Duration.ZERO
    var timeout: Long = Duration.INFINITE.inWholeMilliseconds

    override suspend fun beforeAtbInit() {
        delay(delay)
    }

    override fun beforeAtbInitTimeoutMillis(): Long = timeout
}
