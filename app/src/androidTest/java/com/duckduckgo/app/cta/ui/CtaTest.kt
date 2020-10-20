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
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.orderedTrackingEntities
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyGrade
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.TestEntity
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.CTA_SHOWN
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
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

    @Mock
    private lateinit var mockActivity: FragmentActivity

    @Mock
    private lateinit var mockResources: Resources

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)

        whenever(mockActivity.resources).thenReturn(mockResources)
        whenever(mockResources.getQuantityString(any(), any())).thenReturn("withZero")
        whenever(mockResources.getQuantityString(any(), any(), any())).thenReturn("withMultiple")
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
    fun whenAddCtaToHistoryThenReturnCorrectValue() {
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn(null)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        val testee = DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore)
        val value = testee.addCtaToHistory("test")
        assertEquals("test:0", value)
    }

    @Test
    fun whenAddCtaToHistoryOnDay3ThenReturnCorrectValue() {
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn(null)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(3))

        val testee = DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore)
        val value = testee.addCtaToHistory("test")
        assertEquals("test:3", value)
    }

    @Test
    fun whenAddCtaToHistoryOnDay4ThenReturn3AsDayValue() {
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn(null)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(4))

        val testee = DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore)
        val value = testee.addCtaToHistory("test")
        assertEquals("test:3", value)
    }

    @Test
    fun whenAddCtaToHistoryContainsHistoryThenConcatenateNewValue() {
        val ctaHistory = "s:0-t:1"
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn(ctaHistory)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))

        val testee = DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore)
        val value = testee.addCtaToHistory("test")
        val expectedValue = "$ctaHistory-test:1"

        assertEquals(expectedValue, value)
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
    fun whenCanSendPixelAndCtaNotPartOfHistoryThenReturnTrue() {
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn("s:0")
        val testee = DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore)
        assertTrue(testee.canSendShownPixel())
    }

    @Test
    fun whenCanSendPixelAndCtaIsPartOfHistoryThenReturnFalse() {
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn("i:0-e:0-s:0")

        val testee = DaxBubbleCta.DaxEndCta(mockOnboardingStore, mockAppInstallStore)
        assertFalse(testee.canSendShownPixel())
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
    fun whenMoreThanTwoTrackersBlockedReturnFirstTwoWithMultipleString() {
        val trackers = listOf(
            TestEntity("Facebook", "Facebook", 9.0),
            TestEntity("Other", "Other", 9.0),
            TestEntity("Amazon", "Amazon", 9.0)
        )

        val testee = DaxDialogCta.DaxTrackersBlockedCta(mockOnboardingStore, mockAppInstallStore, trackers, "http://www.trackers.com")
        val value = testee.getDaxText(mockActivity)

        assertEquals("<b>Facebook, Other</b>withMultiple", value)
    }

    @Test
    fun whenTwoTrackersBlockedReturnThemWithZeroString() {
        val trackers = listOf(
            TestEntity("Facebook", "Facebook", 9.0),
            TestEntity("Other", "Other", 9.0)
        )

        val testee = DaxDialogCta.DaxTrackersBlockedCta(mockOnboardingStore, mockAppInstallStore, trackers, "http://www.trackers.com")
        val value = testee.getDaxText(mockActivity)

        assertEquals("<b>Facebook, Other</b>withZero", value)
    }

    @Test
    fun whenTrackersBlockedReturnThemSortingByPrevalence() {
        val trackers = listOf(
            TrackingEvent("facebook.com", "facebook.com", blocked = true, entity = TestEntity("Facebook", "Facebook", 3.0), categories = null),
            TrackingEvent("other.com", "other.com", blocked = true, entity = TestEntity("Other", "Other", 9.0), categories = null)
        )
        val site = site(events = trackers)

        val testee =
            DaxDialogCta.DaxTrackersBlockedCta(mockOnboardingStore, mockAppInstallStore, site.orderedTrackingEntities(), "http://www.trackers.com")
        val value = testee.getDaxText(mockActivity)

        assertEquals("<b>Other, Facebook</b>withZero", value)
    }

    @Test
    fun whenTrackersBlockedReturnOnlyTrackersWithDisplayName() {
        val trackers = listOf(
            TrackingEvent("facebook.com", "facebook.com", blocked = true, entity = TestEntity("Facebook", "Facebook", 3.0), categories = null),
            TrackingEvent("other.com", "other.com", blocked = true, entity = TestEntity("Other", "", 9.0), categories = null)
        )
        val site = site(events = trackers)

        val testee =
            DaxDialogCta.DaxTrackersBlockedCta(mockOnboardingStore, mockAppInstallStore, site.orderedTrackingEntities(), "http://www.trackers.com")
        val value = testee.getDaxText(mockActivity)

        assertEquals("<b>Facebook</b>withZero", value)
    }

    @Test
    fun whenMultipleTrackersFromSameNetworkBlockedReturnOnlyOneWithZeroString() {
        val trackers = listOf(
            TestEntity("Facebook", "Facebook", 9.0),
            TestEntity("Facebook", "Facebook", 9.0),
            TestEntity("Facebook", "Facebook", 9.0)
        )

        val testee = DaxDialogCta.DaxTrackersBlockedCta(mockOnboardingStore, mockAppInstallStore, trackers, "http://www.trackers.com")
        val value = testee.getDaxText(mockActivity)

        assertEquals("<b>Facebook</b>withZero", value)
    }

    @Test
    fun whenCtaIsUseOurAppReturnEmptyOkParameters() {
        val testee = UseOurAppCta()
        assertTrue(testee.pixelOkParameters().isEmpty())
    }

    @Test
    fun whenCtaIsUseOurAppReturnEmptyCancelParameters() {
        val testee = UseOurAppCta()
        assertTrue(testee.pixelCancelParameters().isEmpty())
    }

    @Test
    fun whenCtaIsUseOurAppReturnEmptyShownParameters() {
        val testee = UseOurAppCta()
        assertTrue(testee.pixelShownParameters().isEmpty())
    }

    @Test
    fun whenCtaIsUseOurAppDeletionReturnEmptyOkParameters() {
        val testee = UseOurAppDeletionCta()
        assertTrue(testee.pixelOkParameters().isEmpty())
    }

    @Test
    fun whenCtaIsUseOurAppDeletionReturnEmptyCancelParameters() {
        val testee = UseOurAppDeletionCta()
        assertTrue(testee.pixelCancelParameters().isEmpty())
    }

    @Test
    fun whenCtaIsUseOurAppDeletionReturnEmptyShownParameters() {
        val testee = UseOurAppDeletionCta()
        assertTrue(testee.pixelShownParameters().isEmpty())
    }

    @Test
    fun whenTryClearDataCtaShownThenConcatenateJourneyStoredValueInPixel() {
        val existingJourney = "s:0-t:1"
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn(existingJourney)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        val testee = DaxFireDialogCta.TryClearDataCta(mockOnboardingStore, mockAppInstallStore)
        val expectedValue = "$existingJourney-${testee.ctaPixelParam}:1"

        val value = testee.pixelShownParameters()

        assertEquals(expectedValue, value[CTA_SHOWN])
    }

    private fun site(
        url: String = "http://www.test.com",
        uri: Uri? = Uri.parse(url),
        https: HttpsStatus = HttpsStatus.SECURE,
        trackerCount: Int = 0,
        events: List<TrackingEvent> = emptyList(),
        majorNetworkCount: Int = 0,
        allTrackersBlocked: Boolean = true,
        privacyPractices: PrivacyPractices.Practices = PrivacyPractices.UNKNOWN,
        entity: Entity? = null,
        grade: PrivacyGrade = PrivacyGrade.UNKNOWN,
        improvedGrade: PrivacyGrade = PrivacyGrade.UNKNOWN
    ): Site {
        val site: Site = mock()
        whenever(site.url).thenReturn(url)
        whenever(site.uri).thenReturn(uri)
        whenever(site.https).thenReturn(https)
        whenever(site.entity).thenReturn(entity)
        whenever(site.trackingEvents).thenReturn(events)
        whenever(site.trackerCount).thenReturn(trackerCount)
        whenever(site.majorNetworkCount).thenReturn(majorNetworkCount)
        whenever(site.allTrackersBlocked).thenReturn(allTrackersBlocked)
        whenever(site.privacyPractices).thenReturn(privacyPractices)
        whenever(site.calculateGrades()).thenReturn(Site.SiteGrades(grade, improvedGrade))
        return site
    }
}
