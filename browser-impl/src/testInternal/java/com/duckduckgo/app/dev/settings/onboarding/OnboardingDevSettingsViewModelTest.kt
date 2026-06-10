/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.dev.settings.onboarding

import app.cash.turbine.test
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.cta.ui.CtaViewModel
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OnboardingDevSettingsViewModelTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val userStageStore: UserStageStore = mock()
    private val settingsDataStore: SettingsDataStore = mock()
    private val dismissedCtaDao: DismissedCtaDao = mock()
    private val ctaViewModel: CtaViewModel = mock()

    private val requiredCtas = listOf(
        CtaId.DAX_INTRO,
        CtaId.DAX_DIALOG_SERP,
        CtaId.DAX_DIALOG_TRACKERS_FOUND,
        CtaId.DAX_FIRE_BUTTON,
        CtaId.DAX_END,
        CtaId.DAX_INTRO_PRIVACY_PRO,
    )

    /** requiredCtas + ADD_WIDGET + DAX_INTRO_VISIT_SITE (matches ViewModel's visibleCtaIds()). */
    private val allCtaIds = requiredCtas + listOf(CtaId.ADD_WIDGET, CtaId.DAX_INTRO_VISIT_SITE)

    private val testee = OnboardingDevSettingsViewModel(
        userStageStore = userStageStore,
        settingsDataStore = settingsDataStore,
        dismissedCtaDao = dismissedCtaDao,
        ctaViewModel = ctaViewModel,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenStartWithActiveOnboardingThenViewStateShowsNotCompletedNotSkipped() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(ctaViewModel.requiredDaxOnboardingCtas()).thenReturn(requiredCtas)
        allCtaIds.forEach { ctaId -> whenever(dismissedCtaDao.exists(ctaId)).thenReturn(false) }

        testee.start()

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.onboardingCompleted)
            assertFalse(state.onboardingSkipped)
        }
    }

    @Test
    fun whenStartWithCompletedOnboardingThenViewStateShowsCompletedNotSkipped() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(ctaViewModel.requiredDaxOnboardingCtas()).thenReturn(requiredCtas)
        allCtaIds.forEach { ctaId -> whenever(dismissedCtaDao.exists(ctaId)).thenReturn(true) }

        testee.start()

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.onboardingCompleted)
            assertFalse(state.onboardingSkipped)
        }
    }

    @Test
    fun whenStartWithSkippedOnboardingThenViewStateShowsCompletedAndSkipped() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(settingsDataStore.hideTips).thenReturn(true)
        whenever(ctaViewModel.requiredDaxOnboardingCtas()).thenReturn(requiredCtas)
        allCtaIds.forEach { ctaId -> whenever(dismissedCtaDao.exists(ctaId)).thenReturn(false) }

        testee.start()

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.onboardingCompleted)
            assertTrue(state.onboardingSkipped)
        }
    }

    @Test
    fun whenSubscriptionNotRequiredThenVisibleCtaIdsExcludesSubscriptionCta() = runTest {
        val requiredWithoutSubscription = listOf(
            CtaId.DAX_INTRO,
            CtaId.DAX_DIALOG_SERP,
            CtaId.DAX_DIALOG_TRACKERS_FOUND,
            CtaId.DAX_FIRE_BUTTON,
            CtaId.DAX_END,
        )
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(ctaViewModel.requiredDaxOnboardingCtas()).thenReturn(requiredWithoutSubscription)
        allCtaIds.forEach { ctaId -> whenever(dismissedCtaDao.exists(ctaId)).thenReturn(false) }

        testee.start()

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.visibleCtaIds.contains(CtaId.DAX_INTRO_PRIVACY_PRO))
        }
    }

    @Test
    fun whenOnboardingCompletedToggledOnThenMoveToEstablishedAndInsertAllCtas() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(ctaViewModel.requiredDaxOnboardingCtas()).thenReturn(requiredCtas)
        allCtaIds.forEach { ctaId -> whenever(dismissedCtaDao.exists(ctaId)).thenReturn(false) }

        testee.onOnboardingCompletedToggled(true)

        verify(userStageStore).moveToStage(AppStage.ESTABLISHED)
        allCtaIds.forEach { ctaId -> verify(dismissedCtaDao).insert(DismissedCta(ctaId)) }
    }

    @Test
    fun whenOnboardingCompletedToggledOffThenMoveToDaxOnboardingAndDeleteAllCtas() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(ctaViewModel.requiredDaxOnboardingCtas()).thenReturn(requiredCtas)
        allCtaIds.forEach { ctaId -> whenever(dismissedCtaDao.exists(ctaId)).thenReturn(true) }

        testee.onOnboardingCompletedToggled(false)

        verify(userStageStore).moveToStage(AppStage.DAX_ONBOARDING)
        allCtaIds.forEach { ctaId -> verify(dismissedCtaDao).delete(ctaId) }
    }

    @Test
    fun whenOnboardingSkippedToggledOnThenMoveToEstablishedAndSetHideTipsTrue() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(ctaViewModel.requiredDaxOnboardingCtas()).thenReturn(requiredCtas)
        allCtaIds.forEach { ctaId -> whenever(dismissedCtaDao.exists(ctaId)).thenReturn(false) }

        testee.onOnboardingSkippedToggled(true)

        verify(userStageStore).moveToStage(AppStage.ESTABLISHED)
        verify(settingsDataStore).hideTips = true
    }

    @Test
    fun whenOnboardingSkippedToggledOffThenSetHideTipsFalse() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.ESTABLISHED)
        whenever(settingsDataStore.hideTips).thenReturn(true)
        whenever(ctaViewModel.requiredDaxOnboardingCtas()).thenReturn(requiredCtas)
        allCtaIds.forEach { ctaId -> whenever(dismissedCtaDao.exists(ctaId)).thenReturn(false) }

        testee.onOnboardingSkippedToggled(false)

        verify(settingsDataStore).hideTips = false
    }

    @Test
    fun whenCtaDismissedToggledOnThenInsertCta() = runTest {
        whenever(ctaViewModel.requiredDaxOnboardingCtas()).thenReturn(requiredCtas)
        whenever(dismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(false)

        testee.onCtaDismissedToggled(CtaId.DAX_INTRO, isDismissed = true)

        verify(dismissedCtaDao).insert(DismissedCta(CtaId.DAX_INTRO))
    }

    @Test
    fun whenCtaDismissedToggledOffThenDeleteCta() = runTest {
        whenever(ctaViewModel.requiredDaxOnboardingCtas()).thenReturn(requiredCtas)
        whenever(dismissedCtaDao.exists(CtaId.DAX_INTRO)).thenReturn(true)

        testee.onCtaDismissedToggled(CtaId.DAX_INTRO, isDismissed = false)

        verify(dismissedCtaDao).delete(CtaId.DAX_INTRO)
    }

    @Test
    fun whenAddWidgetToggledThenOnlyDaoUpdatedNoStageChange() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(ctaViewModel.requiredDaxOnboardingCtas()).thenReturn(requiredCtas)
        allCtaIds.forEach { ctaId -> whenever(dismissedCtaDao.exists(ctaId)).thenReturn(false) }

        testee.onCtaDismissedToggled(CtaId.ADD_WIDGET, isDismissed = true)

        verify(dismissedCtaDao).insert(DismissedCta(CtaId.ADD_WIDGET))
        verify(userStageStore, never()).moveToStage(any())
    }

    @Test
    fun isIndependentCtaReturnsTrueOnlyForAddWidget() {
        assertTrue(testee.isIndependentCta(CtaId.ADD_WIDGET))
        assertFalse(testee.isIndependentCta(CtaId.DAX_INTRO))
        assertFalse(testee.isIndependentCta(CtaId.DAX_END))
    }
}
