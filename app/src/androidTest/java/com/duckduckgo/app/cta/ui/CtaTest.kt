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

package com.duckduckgo.app.cta.ui

import com.duckduckgo.app.cta.CtaDaxHelper
import com.duckduckgo.app.cta.CtaHelper
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.CTA_SHOWN
import com.duckduckgo.app.survey.model.Survey
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.util.concurrent.TimeUnit

class CtaTest {

    @Mock
    private lateinit var mockOnboardingStore: OnboardingStore

    @Mock
    private lateinit var mockAppInstallStore: AppInstallStore

    private lateinit var daxCtaHelper: CtaHelper

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)

        daxCtaHelper = CtaDaxHelper(mockOnboardingStore, mockAppInstallStore)
    }

    @Test
    fun whenCtaIsSurveyReturnEmptyOkParameters() {
        val testee = HomePanelCta.Survey(Survey("abc", "http://example.com", 1, Survey.Status.SCHEDULED))
        assertTrue(testee.pixelOkParameters().isEmpty())
    }

    @Test
    fun whenCtaIsSurveyReturnEmptyCancelParameters() {
        val testee = HomePanelCta.Survey(Survey("abc", "http://example.com", 1, Survey.Status.SCHEDULED))
        assertTrue(testee.pixelCancelParameters().isEmpty())
    }

    @Test
    fun whenCtaIsSurveyReturnEmptyShownParameters() {
        val testee = HomePanelCta.Survey(Survey("abc", "http://example.com", 1, Survey.Status.SCHEDULED))
        assertTrue(testee.pixelShownParameters().isEmpty())
    }

    @Test
    fun whenCtaIsAddWidgetAutoReturnEmptyOkParameters() {
        val testee = HomePanelCta.AddWidgetAuto
        assertTrue(testee.pixelOkParameters().isEmpty())
    }

    @Test
    fun whenCtaIsAddWidgetAutoReturnEmptyCancelParameters() {
        val testee = HomePanelCta.AddWidgetAuto
        assertTrue(testee.pixelCancelParameters().isEmpty())
    }

    @Test
    fun whenCtaIsAddWidgetAutoReturnEmptyShownParameters() {
        val testee = HomePanelCta.AddWidgetAuto
        assertTrue(testee.pixelShownParameters().isEmpty())
    }

    @Test
    fun whenCtaIsAddWidgetInstructionsReturnEmptyOkParameters() {
        val testee = HomePanelCta.AddWidgetInstructions
        assertTrue(testee.pixelOkParameters().isEmpty())
    }

    @Test
    fun whenCtaIsAddWidgetInstructionsReturnEmptyCancelParameters() {
        val testee = HomePanelCta.AddWidgetInstructions
        assertTrue(testee.pixelCancelParameters().isEmpty())
    }

    @Test
    fun whenCtaIsAddWidgetInstructionsReturnEmptyShownParameters() {
        val testee = HomePanelCta.AddWidgetInstructions
        assertTrue(testee.pixelShownParameters().isEmpty())
    }

    @Test
    fun whenCtaIsBubbleTypeReturnCorrectCancelParameters() {
        val testee = DaxBubbleCta.DaxIntroCta(daxCtaHelper)
        val value = testee.pixelCancelParameters()

        assertEquals(1, value.size)
        assertTrue(value.containsKey(CTA_SHOWN))
        assertEquals(testee.ctaPixelParam, value[CTA_SHOWN])
    }

    @Test
    fun whenCtaIsBubbleTypeReturnCorrectOkParameters() {
        val testee = DaxBubbleCta.DaxIntroCta(daxCtaHelper)
        val value = testee.pixelOkParameters()

        assertEquals(1, value.size)
        assertTrue(value.containsKey(CTA_SHOWN))
        assertEquals(testee.ctaPixelParam, value[CTA_SHOWN])
    }

    @Test
    fun whenCtaIsBubbleTypeReturnCorrectShownParameters() {
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn(null)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        val testee = DaxBubbleCta.DaxIntroCta(daxCtaHelper)
        val expectedValue = "${testee.ctaPixelParam}:0"

        val value = testee.pixelShownParameters()
        assertEquals(1, value.size)
        assertTrue(value.containsKey(CTA_SHOWN))
        assertEquals(expectedValue, value[CTA_SHOWN])
    }

    @Test
    fun whenCtaIsBubbleTypeThenConcatenateJourneyStoredValueInPixel() {
        val existingJourney = "s:0-t:1"
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn(existingJourney)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        val testee = DaxBubbleCta.DaxEndCta(daxCtaHelper)
        val expectedValue = "$existingJourney-${testee.ctaPixelParam}:1"

        val value = testee.pixelShownParameters()
        assertEquals(expectedValue, value[CTA_SHOWN])
    }

    @Test
    fun whenCtaIsDialogTypeReturnCorrectCancelParameters() {
        val testee = DaxDialogCta.DaxSerpCta(daxCtaHelper)

        val value = testee.pixelCancelParameters()
        assertEquals(1, value.size)
        assertTrue(value.containsKey(CTA_SHOWN))
        assertEquals(testee.ctaPixelParam, value[CTA_SHOWN])
    }

    @Test
    fun whenCtaIsDialogTypeReturnCorrectOkParameters() {
        val testee = DaxDialogCta.DaxSerpCta(daxCtaHelper)

        val value = testee.pixelOkParameters()
        assertEquals(1, value.size)
        assertTrue(value.containsKey(CTA_SHOWN))
        assertEquals(testee.ctaPixelParam, value[CTA_SHOWN])
    }

    @Test
    fun whenCtaIsDialogTypeReturnCorrectShownParameters() {
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn(null)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        val testee = DaxDialogCta.DaxSerpCta(daxCtaHelper)
        val expectedValue = "${testee.ctaPixelParam}:0"

        val value = testee.pixelShownParameters()
        assertEquals(1, value.size)
        assertTrue(value.containsKey(CTA_SHOWN))
        assertEquals(expectedValue, value[CTA_SHOWN])
    }

    @Test
    fun whenCtaIsDialogTypeThenConcatenateJourneyStoredValueInPixel() {
        val existingJourney = "s:0-t:1"
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn(existingJourney)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        val testee = DaxDialogCta.DaxSerpCta(daxCtaHelper)
        val expectedValue = "$existingJourney-${testee.ctaPixelParam}:1"

        val value = testee.pixelShownParameters()
        assertEquals(expectedValue, value[CTA_SHOWN])
    }
}