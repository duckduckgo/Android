/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.onboarding

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class RealContextualOnboardingTimeLimitTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val userStageStore: UserStageStore = mock()
    private val appInstallStore: AppInstallStore = mock()
    private val toggles = FakeFeatureToggleFactory.create(ExtendedOnboardingFeatureToggles::class.java)
    private val appBuildConfig: AppBuildConfig = mock { on { flavor } doReturn BuildFlavor.PLAY }
    private val preferences = InMemorySharedPreferences()
    private val sharedPreferencesProvider: SharedPreferencesProvider = mock {
        on { getSharedPreferences(any(), any(), any()) } doReturn preferences
    }
    private var now = TimeUnit.DAYS.toMillis(1_000)
    private val currentTimeProvider = object : CurrentTimeProvider {
        override fun elapsedRealtime(): Long = now
        override fun currentTimeMillis(): Long = now
        override fun localDateTimeNow(): LocalDateTime = LocalDateTime.now()
    }
    private val lifecycleOwner: LifecycleOwner = mock()

    private val testee = RealContextualOnboardingTimeLimit(
        userStageStore = userStageStore,
        appInstallStore = appInstallStore,
        extendedOnboardingFeatureToggles = toggles,
        appBuildConfig = appBuildConfig,
        sharedPreferencesProvider = sharedPreferencesProvider,
        currentTimeProvider = currentTimeProvider,
        dispatchers = coroutineRule.testDispatcherProvider,
        appCoroutineScope = coroutineRule.testScope,
    )

    @Before
    fun setup() = runTest {
        givenTimeLimitEnabled()
        givenStage(AppStage.DAX_ONBOARDING)
        givenInstalledDaysAgo(days = 1)
    }

    @Test
    fun whenDaysSinceInstallReachLimitThenOnboardingEndedByTimeLimit() = runTest {
        givenInstalledDaysAgo(days = 7)

        testee.endOnboardingIfTimeLimitReached()

        verify(userStageStore).stageCompleted(AppStage.DAX_ONBOARDING)
        assertTrue(testee.wasOnboardingEndedByTimeLimit())
    }

    @Test
    fun whenDaysSinceInstallBelowLimitThenOnboardingNotEnded() = runTest {
        givenInstalledAgo(TimeUnit.DAYS.toMillis(7) - 1)

        testee.endOnboardingIfTimeLimitReached()

        verify(userStageStore, never()).stageCompleted(any())
        assertFalse(testee.wasOnboardingEndedByTimeLimit())
    }

    @Test
    fun whenAppOpensExceedLimitThenOnboardingEndedByTimeLimit() = runTest {
        repeat(29) { testee.onStart(lifecycleOwner) }

        verify(userStageStore).stageCompleted(AppStage.DAX_ONBOARDING)
        assertTrue(testee.wasOnboardingEndedByTimeLimit())
    }

    @Test
    fun whenAppOpensReachButDoNotExceedLimitThenOnboardingNotEnded() = runTest {
        repeat(28) { testee.onStart(lifecycleOwner) }

        verify(userStageStore, never()).stageCompleted(any())
        assertFalse(testee.wasOnboardingEndedByTimeLimit())
    }

    @Test
    fun whenAppOpenedBeforeContextualOnboardingThenAppOpenCounted() = runTest {
        givenStage(AppStage.NEW)
        testee.onStart(lifecycleOwner)
        givenStage(AppStage.DAX_ONBOARDING)

        repeat(28) { testee.onStart(lifecycleOwner) }

        verify(userStageStore).stageCompleted(AppStage.DAX_ONBOARDING)
    }

    @Test
    fun whenTimeLimitDisabledThenOnboardingNotEnded() = runTest {
        toggles.contextualOnboardingTimeLimit().setRawStoredState(Toggle.State(enable = false))
        givenInstalledDaysAgo(days = 30)
        repeat(30) { testee.onStart(lifecycleOwner) }

        testee.endOnboardingIfTimeLimitReached()

        verify(userStageStore, never()).stageCompleted(any())
        assertFalse(testee.wasOnboardingEndedByTimeLimit())
    }

    @Test
    fun whenUserNotInContextualOnboardingThenOnboardingNotEnded() = runTest {
        givenStage(AppStage.ESTABLISHED)
        givenInstalledDaysAgo(days = 30)

        testee.endOnboardingIfTimeLimitReached()

        verify(userStageStore, never()).stageCompleted(any())
        assertFalse(testee.wasOnboardingEndedByTimeLimit())
    }

    @Test
    fun whenUserEstablishedThenAppOpensNotCounted() = runTest {
        givenStage(AppStage.ESTABLISHED)
        repeat(30) { testee.onStart(lifecycleOwner) }
        givenStage(AppStage.DAX_ONBOARDING)

        testee.endOnboardingIfTimeLimitReached()

        verify(userStageStore, never()).stageCompleted(any())
    }

    @Test
    fun whenSettingsSetLimitsThenSettingsLimitsUsed() = runTest {
        givenTimeLimitEnabled(settings = """{"maxDaysSinceInstall": 3, "maxAppOpens": 100}""")
        givenInstalledDaysAgo(days = 3)

        testee.endOnboardingIfTimeLimitReached()

        verify(userStageStore).stageCompleted(AppStage.DAX_ONBOARDING)
    }

    @Test
    fun whenSettingsSetAppOpensLimitThenSettingsLimitUsed() = runTest {
        givenTimeLimitEnabled(settings = """{"maxAppOpens": 2}""")

        repeat(3) { testee.onStart(lifecycleOwner) }

        verify(userStageStore).stageCompleted(AppStage.DAX_ONBOARDING)
    }

    @Test
    fun whenSettingsInvalidThenDefaultLimitsUsed() = runTest {
        givenTimeLimitEnabled(settings = "not-json")
        givenInstalledDaysAgo(days = 6)

        testee.endOnboardingIfTimeLimitReached()
        verify(userStageStore, never()).stageCompleted(any())

        givenInstalledDaysAgo(days = 7)
        testee.endOnboardingIfTimeLimitReached()
        verify(userStageStore).stageCompleted(AppStage.DAX_ONBOARDING)
    }

    @Test
    fun whenSettingsLimitNotPositiveThenDefaultLimitUsed() = runTest {
        givenTimeLimitEnabled(settings = """{"maxDaysSinceInstall": 0, "maxAppOpens": -1}""")
        givenInstalledDaysAgo(days = 6)

        testee.onStart(lifecycleOwner)

        verify(userStageStore, never()).stageCompleted(any())
    }

    @Test
    fun whenInstallTimestampNotRecordedThenDaysLimitIgnored() = runTest {
        whenever(appInstallStore.hasInstallTimestampRecorded()).thenReturn(false)
        whenever(appInstallStore.installTimestamp).thenReturn(0L)

        testee.endOnboardingIfTimeLimitReached()

        verify(userStageStore, never()).stageCompleted(any())
    }

    @Test
    fun whenTimeLimitRestartedThenDaysCountFromRestart() = runTest {
        givenInstalledDaysAgo(days = 30)
        testee.restartTimeLimit()

        testee.endOnboardingIfTimeLimitReached()
        verify(userStageStore, never()).stageCompleted(any())

        now += TimeUnit.DAYS.toMillis(7)
        testee.endOnboardingIfTimeLimitReached()
        verify(userStageStore).stageCompleted(AppStage.DAX_ONBOARDING)
    }

    @Test
    fun whenTimeLimitRestartedThenAppOpensAndEndedStateReset() = runTest {
        givenTimeLimitEnabled(settings = """{"maxAppOpens": 2}""")
        repeat(3) { testee.onStart(lifecycleOwner) }
        assertTrue(testee.wasOnboardingEndedByTimeLimit())
        clearInvocations(userStageStore)

        testee.restartTimeLimit()
        repeat(2) { testee.onStart(lifecycleOwner) }

        assertFalse(testee.wasOnboardingEndedByTimeLimit())
        verify(userStageStore, never()).stageCompleted(any())
    }

    @Test
    fun whenInternalBuildAndOneDaySinceInstallThenOnboardingEndedByTimeLimit() = runTest {
        givenInternalBuild()
        givenInstalledDaysAgo(days = 1)

        testee.endOnboardingIfTimeLimitReached()

        verify(userStageStore).stageCompleted(AppStage.DAX_ONBOARDING)
    }

    @Test
    fun whenInternalBuildAndAppOpensExceedThreeThenOnboardingEndedByTimeLimit() = runTest {
        givenInternalBuild()
        givenInstalledAgo(TimeUnit.HOURS.toMillis(1))

        repeat(3) { testee.onStart(lifecycleOwner) }
        verify(userStageStore, never()).stageCompleted(any())

        testee.onStart(lifecycleOwner)
        verify(userStageStore).stageCompleted(AppStage.DAX_ONBOARDING)
    }

    @Test
    fun whenInternalBuildThenRemoteLimitsIgnored() = runTest {
        givenInternalBuild()
        givenTimeLimitEnabled(settings = """{"maxDaysSinceInstall": 30, "maxAppOpens": 100}""")
        givenInstalledDaysAgo(days = 1)

        testee.endOnboardingIfTimeLimitReached()

        verify(userStageStore).stageCompleted(AppStage.DAX_ONBOARDING)
    }

    private fun givenInternalBuild() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
    }

    private fun givenTimeLimitEnabled(settings: String? = null) {
        toggles.contextualOnboardingTimeLimit().setRawStoredState(Toggle.State(enable = true, settings = settings))
    }

    private suspend fun givenStage(stage: AppStage) {
        whenever(userStageStore.getUserAppStage()).thenReturn(stage)
    }

    private fun givenInstalledDaysAgo(days: Long) = givenInstalledAgo(TimeUnit.DAYS.toMillis(days))

    private fun givenInstalledAgo(millis: Long) {
        whenever(appInstallStore.hasInstallTimestampRecorded()).thenReturn(true)
        whenever(appInstallStore.installTimestamp).thenReturn(now - millis)
    }
}
