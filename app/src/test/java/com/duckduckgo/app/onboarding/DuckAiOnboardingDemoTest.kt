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

import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class DuckAiOnboardingDemoTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val onboardingStore: OnboardingStore = mock()
    private val dismissedCtaDao: DismissedCtaDao = mock()

    private val testee = RealDuckAiOnboardingDemo(
        onboardingStore = onboardingStore,
        dismissedCtaDao = dismissedCtaDao,
        dispatchers = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun `when armed then duck ai onboarding flow is set`() = runTest {
        testee.arm()
        verify(onboardingStore).setDuckAiOnboardingFlow()
    }

    @Test
    fun `when armed then standard dax ctas are dismissed`() = runTest {
        testee.arm()
        verify(dismissedCtaDao).insert(DismissedCta(CtaId.DAX_INTRO))
        verify(dismissedCtaDao).insert(DismissedCta(CtaId.DAX_DIALOG_SERP))
        verify(dismissedCtaDao).insert(DismissedCta(CtaId.DAX_DIALOG_TRACKERS_FOUND))
        verify(dismissedCtaDao).insert(DismissedCta(CtaId.DAX_FIRE_BUTTON))
        verify(dismissedCtaDao).insert(DismissedCta(CtaId.DAX_END))
        verifyNoMoreInteractions(dismissedCtaDao)
    }

    @Test
    fun `when is active then reads duck ai onboarding flow`() {
        whenever(onboardingStore.isDuckAiOnboardingFlow()).thenReturn(true)
        assertTrue(testee.isActive())
    }
}
