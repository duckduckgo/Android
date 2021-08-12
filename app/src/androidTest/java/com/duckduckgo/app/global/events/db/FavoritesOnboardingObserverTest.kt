/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.global.events.db

import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.events.db.FavoritesOnboardingWorkRequestBuilder.Companion.FAVORITES_ONBOARDING_WORK_TAG
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.runBlocking
import com.duckduckgo.app.statistics.VariantManager
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.reflect.jvm.jvmName

@ExperimentalCoroutinesApi
class FavoritesOnboardingObserverTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val workManager: WorkManager by lazy {
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        return@lazy WorkManager.getInstance(context)
    }

    private val mockVariantManager: VariantManager = mock()

    private val userEventsDependencies = object : UserEventsDependencies(context, coroutineRule.testDispatcherProvider) {
        override val variantManager: VariantManager
            get() = this@FavoritesOnboardingObserverTest.mockVariantManager
    }
    private val onboardingWorker = FavoritesOnboardingWorkRequestBuilder(workManager, mockVariantManager)

    private val testee = FavoritesOnboardingObserver(
        appCoroutineScope = TestCoroutineScope(),
        userEventsRepository = userEventsDependencies.userEventsRepository,
        userStageStore = userEventsDependencies.userStageStore,
        favoritesOnboardingWorkRequestBuilder = onboardingWorker,
        variantManager = mockVariantManager
    )

    @Test
    fun whenUserVisitsFirstSiteThenScheduleWork() = coroutineRule.runBlocking {
        givenOnboardingActive()
        givenFavoritesOnboarindExpEnabled()
        testee.siteAddedAsFavoriteOnboarding()

        userEventsDependencies.userEventsRepository.siteVisited("1", "http://example.com", "example")

        assertTrue(getScheduledWorkers(FAVORITES_ONBOARDING_WORK_TAG).any { it.tags.contains(FavoritesOnboardingWorker::class.jvmName) })
    }

    @Test
    fun whenVisitedSiteWithoutPayloadThenDoNotScheduleWork() = coroutineRule.runBlocking {
        givenOnboardingActive()
        givenFavoritesOnboarindExpEnabled()
        testee.siteAddedAsFavoriteOnboarding()

        userEventsDependencies.userEventsStore.registerUserEvent(UserEventKey.FIRST_NON_SERP_VISITED_SITE)

        assertFalse(getScheduledWorkers(FAVORITES_ONBOARDING_WORK_TAG).any { it.tags.contains(FavoritesOnboardingWorker::class.jvmName) })
    }

    @Test
    fun whenFireButtonPressedThenUserClearedDataRecentlyTrue() = coroutineRule.runBlocking {
        givenFavoritesOnboarindExpEnabled()
        testee.siteAddedAsFavoriteOnboarding()

        userEventsDependencies.userEventsStore.registerUserEvent(UserEventKey.FIRE_BUTTON_EXECUTED)

        assertTrue(testee.userClearedDataRecently)
    }

    @Suppress("SameParameterValue")
    private fun getScheduledWorkers(tag: String): List<WorkInfo> {
        return workManager
            .getWorkInfosByTag(tag)
            .get()
            .filter { it.state == WorkInfo.State.ENQUEUED }
    }

    private fun givenFavoritesOnboarindExpEnabled() {
        whenever(mockVariantManager.getVariant()).thenReturn(VariantManager.ACTIVE_VARIANTS.first { it.key == "po" })
    }

    private suspend fun givenOnboardingActive() {
        userEventsDependencies.userStageStore.moveToStage(AppStage.DAX_ONBOARDING)
    }
}
