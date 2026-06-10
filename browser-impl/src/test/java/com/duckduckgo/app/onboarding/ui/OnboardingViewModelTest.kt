/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui

import android.annotation.SuppressLint
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.browser.newaddressbaroption.RealNewAddressBarOptionManager
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.FullOnboardingSkipper.ViewState
import com.duckduckgo.app.onboarding.ui.OnboardingViewModel.ExtendedOnboardingFlow.DEFAULT_WITHOUT_INTRO_CTA
import com.duckduckgo.app.onboarding.ui.OnboardingViewModel.ExtendedOnboardingFlow.DUCK_AI_FOCUSED
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@Suppress("EXPERIMENTAL_API_USAGE")
class OnboardingViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private var userStageStore: UserStageStore = mock()

    private val pageLayout: OnboardingPageManager = mock()

    private val onboardingSkipper: OnboardingSkipper = mock()

    private val appBuildConfig: AppBuildConfig = mock()

    private val newAddressBarOptionManager: RealNewAddressBarOptionManager = mock()

    private val dismissedCtaDao: DismissedCtaDao = mock()

    private val onboardingStore: OnboardingStore = mock()

    private val testee: OnboardingViewModel by lazy {
        OnboardingViewModel(
            userStageStore = userStageStore,
            pageLayoutManager = pageLayout,
            dispatchers = coroutineRule.testDispatcherProvider,
            onboardingSkipper = onboardingSkipper,
            appBuildConfig = appBuildConfig,
            newAddressBarOptionManager = newAddressBarOptionManager,
            dismissedCtaDao = dismissedCtaDao,
            onboardingStore = onboardingStore,
        )
    }

    @Test
    fun whenOnboardingDoneThenCompleteStage() = runTest {
        testee.onOnboardingDone()
        verify(userStageStore).stageCompleted(AppStage.NEW)
    }

    @Test
    fun whenOnboardingDoneWithDefaultFlowThenNoCtasDismissedAndDuckAiOnboardingFlowNotSet() = runTest {
        testee.onOnboardingDone()

        verifyNoInteractions(dismissedCtaDao)
        verify(onboardingStore, never()).setDuckAiOnboardingFlow()
    }

    @Test
    fun whenOnboardingDoneWithDuckAiFocusedFlowThenDuckAiOnboardingFlowIsSet() = runTest {
        testee.onOnboardingDone(extendedOnboardingFlow = DUCK_AI_FOCUSED)

        verify(onboardingStore).setDuckAiOnboardingFlow()
    }

    @Test
    fun whenOnboardingDoneWithDuckAiFocusedFlowThenStandardDaxCtasAreDismissed() = runTest {
        testee.onOnboardingDone(extendedOnboardingFlow = DUCK_AI_FOCUSED)

        verify(dismissedCtaDao).insert(DismissedCta(CtaId.DAX_INTRO))
        verify(dismissedCtaDao).insert(DismissedCta(CtaId.DAX_DIALOG_SERP))
        verify(dismissedCtaDao).insert(DismissedCta(CtaId.DAX_DIALOG_TRACKERS_FOUND))
        verify(dismissedCtaDao).insert(DismissedCta(CtaId.DAX_FIRE_BUTTON))
        verify(dismissedCtaDao).insert(DismissedCta(CtaId.DAX_END))
    }

    @Test
    fun whenOnboardingDoneWithDefaultWithoutIntroCtaFlowThenOnlyIntroCtaIsDismissed() = runTest {
        testee.onOnboardingDone(extendedOnboardingFlow = DEFAULT_WITHOUT_INTRO_CTA)

        verify(dismissedCtaDao).insert(DismissedCta(CtaId.DAX_INTRO))
        verifyNoMoreInteractions(dismissedCtaDao)
        verify(onboardingStore, never()).setDuckAiOnboardingFlow()
    }

    @Test
    fun whenAppBuildConfigPreventsSkippingOnboardingThenOnboardingSkipperNotInteractedWith() = runTest {
        configureAppBuildConfigPreventsSkipping()
        testee.initializeOnboardingSkipper()
        verifyNoInteractions(onboardingSkipper)
    }

    @Test
    fun whenAppBuildConfigAllowsSkippingOnboardingAndPrivacyConfigDownloadedSkippingIsPossible() = runTest {
        configureAppBuildConfigAllowsSkipping()
        configureSkipperFlow()
        testee.initializeOnboardingSkipper()
        verify(onboardingSkipper).privacyConfigDownloaded
    }

    @Test
    fun whenOnOnboardingSkippedCalledThenMarkOnboardingAsCompleted() = runTest {
        testee.onOnboardingSkipped()
        verify(onboardingSkipper).markOnboardingAsCompleted()
    }

    @Test
    fun whenDevOnlyFullyCompleteAllOnboardingCalledThenMarkOnboardingAsCompletedAndSetAsShown() = runTest {
        testee.devOnlyFullyCompleteAllOnboarding()

        verify(onboardingSkipper).markOnboardingAsCompleted()
        verify(newAddressBarOptionManager).setAsShown()
    }

    @Test
    fun whenInitializePagesCalledThenBuildPageBlueprints() {
        testee.initializePages()
        verify(pageLayout).buildPageBlueprints()
    }

    private fun configureSkipperFlow() = runTest {
        val flow = MutableSharedFlow<ViewState>()
        flow.emit(ViewState(skipOnboardingPossible = true))
        whenever(onboardingSkipper.privacyConfigDownloaded).thenReturn(flow)
    }

    private fun configureAppBuildConfigAllowsSkipping() {
        whenever(appBuildConfig.canSkipOnboarding).thenReturn(true)
    }

    private fun configureAppBuildConfigPreventsSkipping() {
        whenever(appBuildConfig.canSkipOnboarding).thenReturn(false)
    }
}
