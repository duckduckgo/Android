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

import android.content.res.Resources
import androidx.fragment.app.FragmentActivity
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.CTA_SHOWN
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.trackerdetection.model.TrackerNetwork
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import java.util.concurrent.TimeUnit

class CtaTest {

    @Mock
    private lateinit var mockOnboardingStore: OnboardingStore

    @Mock
    private lateinit var mockAppInstallStore: AppInstallStore

    @Mock
    private lateinit var mockActivity: FragmentActivity

    @Mock
    private lateinit var mockResources: Resources

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)

        whenever(mockActivity.resources).thenReturn(mockResources)
        whenever(mockResources.getString(any(), any())).thenReturn("withZero")
        whenever(mockResources.getQuantityString(any(), any(), any(), any())).thenReturn("withMultiple")
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
    fun whenCtaIsSurveyReturnNullDialogCta() {
        val testee = HomePanelCta.Survey(Survey("abc", "http://example.com", 1, Survey.Status.SCHEDULED))
        assertNull(testee.createDialogCta(mock(FragmentActivity::class.java)))
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
    fun whenCtaIsAddWidgetAutoReturnNullDialogCta() {
        val testee = HomePanelCta.AddWidgetAuto
        assertNull(testee.createDialogCta(mock(FragmentActivity::class.java)))
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
    fun whenCtaIsAddWidgetInstructionsReturnNullDialogCta() {
        val testee = HomePanelCta.AddWidgetInstructions
        assertNull(testee.createDialogCta(mock(FragmentActivity::class.java)))
    }

    @Test
    fun whenCtaIsBubbleTypeReturnCorrectCancelParameters() {
        val testee = DaxBubbleCta.DaxIntroCta(mockOnboardingStore, mockAppInstallStore)
        val value = testee.pixelCancelParameters()

        assertEquals(1, value.size)
        assertTrue(value.containsKey(CTA_SHOWN))
        assertEquals(testee.ctaPixelParam, value[CTA_SHOWN])
    }

    @Test
    fun whenCtaIsBubbleTypeReturnCorrectOkParameters() {
        val testee = DaxBubbleCta.DaxIntroCta(mockOnboardingStore, mockAppInstallStore)
        val value = testee.pixelOkParameters()

        assertEquals(1, value.size)
        assertTrue(value.containsKey(CTA_SHOWN))
        assertEquals(testee.ctaPixelParam, value[CTA_SHOWN])
    }

    @Test
    fun whenCtaIsBubbleTypeReturnCorrectShownParameters() {
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn(null)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        val testee = DaxBubbleCta.DaxIntroCta(mockOnboardingStore, mockAppInstallStore)
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
        val testee = DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore)
        val expectedValue = "$existingJourney-${testee.ctaPixelParam}:1"

        val value = testee.pixelShownParameters()
        assertEquals(expectedValue, value[CTA_SHOWN])
    }

    @Test
    fun whenCtaIsDialogTypeReturnCorrectCancelParameters() {
        val testee = DaxDialogCta.DaxSerpCta(mockOnboardingStore, mockAppInstallStore)

        val value = testee.pixelCancelParameters()
        assertEquals(1, value.size)
        assertTrue(value.containsKey(CTA_SHOWN))
        assertEquals(testee.ctaPixelParam, value[CTA_SHOWN])
    }

    @Test
    fun whenCtaIsDialogTypeReturnCorrectOkParameters() {
        val testee = DaxDialogCta.DaxSerpCta(mockOnboardingStore, mockAppInstallStore)

        val value = testee.pixelOkParameters()
        assertEquals(1, value.size)
        assertTrue(value.containsKey(CTA_SHOWN))
        assertEquals(testee.ctaPixelParam, value[CTA_SHOWN])
    }

    @Test
    fun whenCtaIsDialogTypeReturnCorrectShownParameters() {
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn(null)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        val testee = DaxDialogCta.DaxSerpCta(mockOnboardingStore, mockAppInstallStore)
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
        val testee = DaxDialogCta.DaxSerpCta(mockOnboardingStore, mockAppInstallStore)
        val expectedValue = "$existingJourney-${testee.ctaPixelParam}:1"

        val value = testee.pixelShownParameters()
        assertEquals(expectedValue, value[CTA_SHOWN])
    }

    @Test
    fun whenMoreThanTwoMajorTrackersBlockedReturnFirstTwoWithMultipleString() {
        val trackers = listOf(
            TrackingEvent("facebook.com", "facebook.com", TrackerNetwork(isMajor = true, name = "Facebook"), blocked = true, entity = "Facebook"),
            TrackingEvent("other.com", "other.com", TrackerNetwork(isMajor = true, name = "Other"), blocked = true, entity = "Other"),
            TrackingEvent("amazon.com", "amazon.com", TrackerNetwork(isMajor = true, name = "Amazon.com"), blocked = true, entity = "Other")
        )

        val testee = DaxDialogCta.DaxTrackersBlockedCta(mockOnboardingStore, mockAppInstallStore, trackers, "http://www.trackers.com")
        val value = testee.getDaxText(mockActivity)

        assertEquals("<b>Facebook, Other</b>withMultiple", value)
    }

    @Test
    fun whenTrackerHasDifferentNameThenReturnNameInTheNetworkNamesMap() {
        val trackers = listOf(
            TrackingEvent("facebook.com", "facebook.com", TrackerNetwork(isMajor = true, name = "Facebook"), blocked = true, entity = "Facebook"),
            TrackingEvent("amazon.com", "amazon.com", TrackerNetwork(isMajor = true, name = "Amazon.com"), blocked = true, entity = "Other"),
            TrackingEvent("other.com", "other.com", TrackerNetwork(isMajor = true, name = "Other"), blocked = true, entity = "Other")
        )

        val testee = DaxDialogCta.DaxTrackersBlockedCta(mockOnboardingStore, mockAppInstallStore, trackers, "http://www.trackers.com")
        val value = testee.getDaxText(mockActivity)

        assertEquals("<b>Facebook, Amazon</b>withMultiple", value)
    }

    @Test
    fun whenTwoMajorTrackersBlockedReturnThemWithMultipleString() {
        val trackers = listOf(
            TrackingEvent("facebook.com", "facebook.com", TrackerNetwork(isMajor = true, name = "Facebook"), blocked = true, entity = "Facebook"),
            TrackingEvent("other.com", "other.com", TrackerNetwork(isMajor = true, name = "Other"), blocked = true, entity = "Other")
        )

        val testee = DaxDialogCta.DaxTrackersBlockedCta(mockOnboardingStore, mockAppInstallStore, trackers, "http://www.trackers.com")
        val value = testee.getDaxText(mockActivity)

        assertEquals("<b>Facebook, Other</b>withZero", value)
    }

    @Test
    fun whenOneMajorTrackersBlockedReturnItWithMultipleString() {
        val trackers = listOf(
            TrackingEvent("facebook.com", "facebook.com", TrackerNetwork(isMajor = true, name = "Facebook"), blocked = true, entity = "Facebook"),
            TrackingEvent("other.com", "other.com", TrackerNetwork(isMajor = false, name = "Other"), blocked = true, entity = "Other")
        )

        val testee = DaxDialogCta.DaxTrackersBlockedCta(mockOnboardingStore, mockAppInstallStore, trackers, "http://www.trackers.com")
        val value = testee.getDaxText(mockActivity)

        assertEquals("<b>Facebook</b>withMultiple", value)
    }

    @Test
    fun whenMultipleTrackersFromSameNetworkBlockedReturnOnlyOneWithMultipleString() {
        val trackers = listOf(
            TrackingEvent("facebook.com", "facebook.com", TrackerNetwork(isMajor = true, name = "Facebook"), blocked = true, entity = "Facebook"),
            TrackingEvent("facebook.com", "facebook.com", TrackerNetwork(isMajor = true, name = "Facebook"), blocked = true, entity = "Facebook"),
            TrackingEvent("facebook.com", "facebook.com", TrackerNetwork(isMajor = true, name = "Facebook"), blocked = true, entity = "Facebook")
        )

        val testee = DaxDialogCta.DaxTrackersBlockedCta(mockOnboardingStore, mockAppInstallStore, trackers, "http://www.trackers.com")
        val value = testee.getDaxText(mockActivity)

        assertEquals("<b>Facebook</b>withMultiple", value)
    }
}