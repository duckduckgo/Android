/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.onboarding.store

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.runBlocking
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.VariantManager.Companion.DEFAULT_VARIANT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class AppUserStageStoreTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val userStageDao: UserStageDao = mock()
    private val variantManager: VariantManager = mock()
    private val appInstallStore: AppInstallStore = mock()

    private val testee = AppUserStageStore(userStageDao, coroutineRule.testDispatcherProvider, variantManager, appInstallStore, TestCoroutineScope())

    @Test
    fun whenGetUserAppStageThenReturnCurrentStage() = coroutineRule.runBlocking {
        givenCurrentStage(AppStage.DAX_ONBOARDING)

        val userAppStage = testee.getUserAppStage()

        assertEquals(AppStage.DAX_ONBOARDING, userAppStage)
    }

    @Test
    fun whenStageNewCompletedThenStageDaxOnboardingReturned() = coroutineRule.runBlocking {
        givenCurrentStage(AppStage.NEW)

        val nextStage = testee.stageCompleted(AppStage.NEW)

        assertEquals(AppStage.DAX_ONBOARDING, nextStage)
    }

    @Test
    fun whenStageDaxOnboardingCompletedThenStageEstablishedReturned() = coroutineRule.runBlocking {
        givenCurrentStage(AppStage.DAX_ONBOARDING)

        val nextStage = testee.stageCompleted(AppStage.DAX_ONBOARDING)

        assertEquals(AppStage.ESTABLISHED, nextStage)
    }

    @Test
    fun whenStageUseOurAppNotificationCompletedThenStageEstablishedReturned() = coroutineRule.runBlocking {
        givenCurrentStage(AppStage.USE_OUR_APP_NOTIFICATION)

        val nextStage = testee.stageCompleted(AppStage.USE_OUR_APP_NOTIFICATION)

        assertEquals(AppStage.ESTABLISHED, nextStage)
    }

    @Test
    fun whenStageUseOurAppOnboardingCompletedThenStageEstablishedReturned() = coroutineRule.runBlocking {
        givenCurrentStage(AppStage.USE_OUR_APP_ONBOARDING)

        val nextStage = testee.stageCompleted(AppStage.USE_OUR_APP_ONBOARDING)

        assertEquals(AppStage.ESTABLISHED, nextStage)
    }

    @Test
    fun whenStageEstablishedCompletedThenStageEstablishedReturned() = coroutineRule.runBlocking {
        givenCurrentStage(AppStage.ESTABLISHED)

        val nextStage = testee.stageCompleted(AppStage.ESTABLISHED)

        assertEquals(AppStage.ESTABLISHED, nextStage)
    }

    @Test
    fun whenMoveToStageThenUpdateUserStageInDao() = coroutineRule.runBlocking {
        testee.moveToStage(AppStage.USE_OUR_APP_ONBOARDING)
        verify(userStageDao).updateUserStage(AppStage.USE_OUR_APP_ONBOARDING)
    }

    @Test
    fun whenAppResumedAndInstalledFor3DaysAndKillOnboardingFeatureNotActiveIfUserInOnboardingThenDoNotUpdateUserStage() = coroutineRule.runBlocking {
        givenDefaultVariant()
        givenCurrentStage(AppStage.DAX_ONBOARDING)
        givenAppInstalledByDays(days = 3)

        testee.onAppResumed()

        verify(userStageDao, never()).updateUserStage(AppStage.ESTABLISHED)
    }

    @Test
    fun whenAppResumedAndInstalledFor3DaysAndKillOnboardingFeatureActiveIfUserInOnboardingThenMoveToEstablished() = coroutineRule.runBlocking {
        givenKillOnboardingFeature()
        givenCurrentStage(AppStage.DAX_ONBOARDING)
        givenAppInstalledByDays(days = 3)

        testee.onAppResumed()

        verify(userStageDao).updateUserStage(AppStage.ESTABLISHED)
    }

    @Test
    fun whenAppResumedAndInstalledForLess3DaysAndKillOnboardingFeatureActiveThenDoNotUpdateUserStage() = coroutineRule.runBlocking {
        givenKillOnboardingFeature()
        givenCurrentStage(AppStage.DAX_ONBOARDING)
        givenAppInstalledByDays(days = 2)

        testee.onAppResumed()

        verify(userStageDao, never()).updateUserStage(any())
    }

    private fun givenAppInstalledByDays(days: Long) {
        whenever(appInstallStore.hasInstallTimestampRecorded()).thenReturn(true)
        whenever(appInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days) - 1)
    }

    private fun givenDefaultVariant() {
        whenever(variantManager.getVariant(any())).thenReturn(DEFAULT_VARIANT)
    }

    private fun givenKillOnboardingFeature() {
        whenever(variantManager.getVariant()).thenReturn(
            Variant("test", features = listOf(VariantManager.VariantFeature.KillOnboarding), filterBy = { true })
        )
    }

    private suspend fun givenCurrentStage(appStage: AppStage) {
        whenever(userStageDao.currentUserAppStage()).thenReturn(UserStage(appStage = appStage))
    }
}
