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

package com.duckduckgo.app.onboarding

import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.onboarding.ui.page.extendedonboarding.ExtendedOnboardingFeatureToggles
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OnboardingFlowCheckerImplTest {

    private val dismissedCtaDao: DismissedCtaDao = mock()
    private val extendedOnboardingFeatureToggles: ExtendedOnboardingFeatureToggles = mock()
    private val settingsDataStore: SettingsDataStore = mock()
    private val noBrowserCtasToggle: Toggle = mock()

    private lateinit var testee: OnboardingFlowCheckerImpl

    @Before
    fun setup() {
        whenever(extendedOnboardingFeatureToggles.noBrowserCtas()).thenReturn(noBrowserCtasToggle)

        testee = OnboardingFlowCheckerImpl(
            dismissedCtaDao = dismissedCtaDao,
            extendedOnboardingFeatureToggles = extendedOnboardingFeatureToggles,
            settingsDataStore = settingsDataStore,
        )
    }

    @Test
    fun whenDaxEndCtaIsDismissedThenOnboardingIsComplete() {
        whenever(dismissedCtaDao.exists(CtaId.DAX_END)).thenReturn(true)
        whenever(noBrowserCtasToggle.isEnabled()).thenReturn(false)
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(dismissedCtaDao.exists(CtaId.ADD_WIDGET)).thenReturn(false)

        val result = testee.isOnboardingComplete()

        assertTrue(result)
    }

    @Test
    fun whenNoBrowserCtasExperimentIsEnabledThenOnboardingIsComplete() {
        whenever(dismissedCtaDao.exists(CtaId.DAX_END)).thenReturn(false)
        whenever(noBrowserCtasToggle.isEnabled()).thenReturn(true)
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(dismissedCtaDao.exists(CtaId.ADD_WIDGET)).thenReturn(false)

        val result = testee.isOnboardingComplete()

        assertTrue(result)
    }

    @Test
    fun whenHideTipsIsTrueThenOnboardingIsComplete() {
        whenever(dismissedCtaDao.exists(CtaId.DAX_END)).thenReturn(false)
        whenever(noBrowserCtasToggle.isEnabled()).thenReturn(false)
        whenever(settingsDataStore.hideTips).thenReturn(true)
        whenever(dismissedCtaDao.exists(CtaId.ADD_WIDGET)).thenReturn(false)

        val result = testee.isOnboardingComplete()

        assertTrue(result)
    }

    @Test
    fun whenAddWidgetCtaIsDismissedThenOnboardingIsComplete() {
        whenever(dismissedCtaDao.exists(CtaId.DAX_END)).thenReturn(false)
        whenever(noBrowserCtasToggle.isEnabled()).thenReturn(false)
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(dismissedCtaDao.exists(CtaId.ADD_WIDGET)).thenReturn(true)

        val result = testee.isOnboardingComplete()

        assertTrue(result)
    }

    @Test
    fun whenNoCompletionConditionsAreMetThenOnboardingIsNotComplete() {
        whenever(dismissedCtaDao.exists(CtaId.DAX_END)).thenReturn(false)
        whenever(noBrowserCtasToggle.isEnabled()).thenReturn(false)
        whenever(settingsDataStore.hideTips).thenReturn(false)
        whenever(dismissedCtaDao.exists(CtaId.ADD_WIDGET)).thenReturn(false)

        val result = testee.isOnboardingComplete()

        assertFalse(result)
    }

    @Test
    fun whenMultipleCompletionConditionsAreMetThenOnboardingIsComplete() {
        whenever(dismissedCtaDao.exists(CtaId.DAX_END)).thenReturn(true)
        whenever(noBrowserCtasToggle.isEnabled()).thenReturn(true)
        whenever(settingsDataStore.hideTips).thenReturn(true)
        whenever(dismissedCtaDao.exists(CtaId.ADD_WIDGET)).thenReturn(true)

        val result = testee.isOnboardingComplete()

        assertTrue(result)
    }
}
