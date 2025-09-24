/*
 * Copyright (c) 2022 DuckDuckGo
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
import com.duckduckgo.browser.ui.omnibar.OmnibarPosition.BOTTOM
import com.duckduckgo.browser.ui.omnibar.OmnibarPosition.TOP
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.orderedTrackerBlockedEntities
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentManager
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.TestingEntity
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.CTA_SHOWN
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CtaTest {

    @Mock
    private lateinit var mockOnboardingStore: OnboardingStore

    @Mock
    private lateinit var mockAppInstallStore: AppInstallStore

    @Mock
    private lateinit var mockActivity: FragmentActivity

    @Mock
    private lateinit var mockResources: Resources

    @Mock
    private lateinit var mockSettingsDataStore: SettingsDataStore

    @Mock
    private lateinit var mockOnboardingDesignExperimentManager: OnboardingDesignExperimentManager

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        whenever(mockOnboardingDesignExperimentManager.isBbEnrolledAndEnabled()).thenReturn(false)

        whenever(mockActivity.resources).thenReturn(mockResources)
        whenever(mockResources.getQuantityString(any(), any())).thenReturn("withZero")
        whenever(mockResources.getQuantityString(any(), any(), any())).thenReturn("withMultiple")
        whenever(mockSettingsDataStore.omnibarPosition).thenReturn(TOP)
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
        val testee = DaxBubbleCta.DaxIntroSearchOptionsCta(mockOnboardingStore, mockAppInstallStore)
        val value = testee.pixelCancelParameters()

        assertEquals(1, value.size)
        assertTrue(value.containsKey(CTA_SHOWN))
        assertEquals(testee.ctaPixelParam, value[CTA_SHOWN])
    }

    @Test
    fun whenCtaIsBubbleTypeReturnCorrectOkParameters() {
        val testee = DaxBubbleCta.DaxIntroSearchOptionsCta(mockOnboardingStore, mockAppInstallStore)
        val value = testee.pixelOkParameters()

        assertEquals(1, value.size)
        assertTrue(value.containsKey(CTA_SHOWN))
        assertEquals(testee.ctaPixelParam, value[CTA_SHOWN])
    }

    @Test
    fun whenCtaIsBubbleTypeReturnCorrectShownParameters() {
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn(null)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())

        val testee = DaxBubbleCta.DaxIntroSearchOptionsCta(mockOnboardingStore, mockAppInstallStore)
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
    fun whenOmnibarPositionIsTopKeepTopPointingEmoji() {
        val inputString = "<![CDATA[&#160;were trying to track you here. I blocked them!<br/><br/>☝️ Tap the shield for more info.️]]"
        assertEquals(inputString.getStringForOmnibarPosition(TOP), inputString)
    }

    @Test
    fun whenOmnibarPositionIsBottomUpdateHandEmojiToPointDown() {
        val inputString = "<![CDATA[&#160;were trying to track you here. I blocked them!<br/><br/>☝️ Tap the shield for more info.️]]"
        val expectedString = "<![CDATA[&#160;were trying to track you here. I blocked them!<br/><br/>\uD83D\uDC47️ Tap the shield for more info.️]]"
        assertEquals(inputString.getStringForOmnibarPosition(BOTTOM), expectedString)
    }

    @Test
    fun whenCanSendPixelAndCtaNotPartOfHistoryButIsASubstringThenReturnTrue() {
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn("s:0-te:0")
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
        val testee = OnboardingDaxDialogCta.DaxSerpCta(mockOnboardingStore, mockAppInstallStore, mockOnboardingDesignExperimentManager)

        val value = testee.pixelCancelParameters()
        assertEquals(1, value.size)
        assertTrue(value.containsKey(CTA_SHOWN))
        assertEquals(testee.ctaPixelParam, value[CTA_SHOWN])
    }

    @Test
    fun whenCtaIsDialogTypeReturnCorrectOkParameters() {
        val testee = OnboardingDaxDialogCta.DaxSerpCta(mockOnboardingStore, mockAppInstallStore, mockOnboardingDesignExperimentManager)

        val value = testee.pixelOkParameters()
        assertEquals(1, value.size)
        assertTrue(value.containsKey(CTA_SHOWN))
        assertEquals(testee.ctaPixelParam, value[CTA_SHOWN])
    }

    @Test
    fun whenCtaIsDialogTypeReturnCorrectShownParameters() {
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn(null)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis())
        val testee = OnboardingDaxDialogCta.DaxSerpCta(mockOnboardingStore, mockAppInstallStore, mockOnboardingDesignExperimentManager)
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
        val testee = OnboardingDaxDialogCta.DaxSerpCta(mockOnboardingStore, mockAppInstallStore, mockOnboardingDesignExperimentManager)
        val expectedValue = "$existingJourney-${testee.ctaPixelParam}:1"

        val value = testee.pixelShownParameters()
        assertEquals(expectedValue, value[CTA_SHOWN])
    }

    @Test
    fun whenMoreThanTwoTrackersBlockedReturnFirstTwoWithMultipleString() {
        val trackers = listOf(
            TestingEntity("Facebook", "Facebook", 9.0),
            TestingEntity("Other", "Other", 9.0),
            TestingEntity("Amazon", "Amazon", 9.0),
        )

        val testee = OnboardingDaxDialogCta.DaxTrackersBlockedCta(
            mockOnboardingStore,
            mockAppInstallStore,
            trackers,
            mockSettingsDataStore,
            mockOnboardingDesignExperimentManager,
        )
        val value = testee.getTrackersDescription(mockActivity, trackers)

        assertEquals("<b>Facebook, Other</b>withMultiple", value)
    }

    @Test
    fun whenMoreThanTwoTrackersBlockedAndBBExperimentEnabledReturnFirstTwoWithMultipleString() {
        whenever(mockOnboardingDesignExperimentManager.isBbEnrolledAndEnabled()).thenReturn(true)

        val trackers = listOf(
            TestingEntity("Facebook", "Facebook", 9.0),
            TestingEntity("Other", "Other", 9.0),
            TestingEntity("Amazon", "Amazon", 9.0),
        )

        val testee = OnboardingDaxDialogCta.DaxTrackersBlockedCta(
            mockOnboardingStore,
            mockAppInstallStore,
            trackers,
            mockSettingsDataStore,
            mockOnboardingDesignExperimentManager,
        )
        val value = testee.getTrackersDescription(mockActivity, trackers)

        assertEquals("Facebook, OtherwithMultiple", value)
    }

    @Test
    fun whenTwoTrackersBlockedReturnThemWithZeroString() {
        val trackers = listOf(
            TestingEntity("Facebook", "Facebook", 9.0),
            TestingEntity("Other", "Other", 9.0),
        )

        val testee = OnboardingDaxDialogCta.DaxTrackersBlockedCta(
            mockOnboardingStore,
            mockAppInstallStore,
            trackers,
            mockSettingsDataStore,
            mockOnboardingDesignExperimentManager,
        )
        val value = testee.getTrackersDescription(mockActivity, trackers)

        assertEquals("<b>Facebook, Other</b>withZero", value)
    }

    @Test
    fun whenTwoTrackersBlockedAndBBExperimentEnabledReturnThemWithZeroString() {
        whenever(mockOnboardingDesignExperimentManager.isBbEnrolledAndEnabled()).thenReturn(true)

        val trackers = listOf(
            TestingEntity("Facebook", "Facebook", 9.0),
            TestingEntity("Other", "Other", 9.0),
        )

        val testee = OnboardingDaxDialogCta.DaxTrackersBlockedCta(
            mockOnboardingStore,
            mockAppInstallStore,
            trackers,
            mockSettingsDataStore,
            mockOnboardingDesignExperimentManager,
        )
        val value = testee.getTrackersDescription(mockActivity, trackers)

        assertEquals("Facebook, OtherwithZero", value)
    }

    @Test
    fun whenTrackersBlockedReturnThemSortingByPrevalence() {
        val trackers = listOf(
            TrackingEvent(
                documentUrl = "facebook.com",
                trackerUrl = "facebook.com",
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
                entity = TestingEntity("Facebook", "Facebook", 3.0),
                categories = null,
                surrogateId = null,
            ),
            TrackingEvent(
                documentUrl = "other.com",
                trackerUrl = "other.com",
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
                entity = TestingEntity("Other", "Other", 9.0),
                categories = null,
                surrogateId = null,
            ),
        )
        val site = site(events = trackers)

        val testee =
            OnboardingDaxDialogCta.DaxTrackersBlockedCta(
                mockOnboardingStore,
                mockAppInstallStore,
                site.orderedTrackerBlockedEntities(),
                mockSettingsDataStore,
                mockOnboardingDesignExperimentManager,
            )
        val value = testee.getTrackersDescription(mockActivity, site.orderedTrackerBlockedEntities())

        assertEquals("<b>Other, Facebook</b>withZero", value)
    }

    @Test
    fun whenTrackersBlockedAndBBExperimentEnabledReturnThemSortingByPrevalence() {
        whenever(mockOnboardingDesignExperimentManager.isBbEnrolledAndEnabled()).thenReturn(true)

        val trackers = listOf(
            TrackingEvent(
                documentUrl = "facebook.com",
                trackerUrl = "facebook.com",
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
                entity = TestingEntity("Facebook", "Facebook", 3.0),
                categories = null,
                surrogateId = null,
            ),
            TrackingEvent(
                documentUrl = "other.com",
                trackerUrl = "other.com",
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
                entity = TestingEntity("Other", "Other", 9.0),
                categories = null,
                surrogateId = null,
            ),
        )
        val site = site(events = trackers)

        val testee =
            OnboardingDaxDialogCta.DaxTrackersBlockedCta(
                mockOnboardingStore,
                mockAppInstallStore,
                site.orderedTrackerBlockedEntities(),
                mockSettingsDataStore,
                mockOnboardingDesignExperimentManager,
            )
        val value = testee.getTrackersDescription(mockActivity, site.orderedTrackerBlockedEntities())

        assertEquals("Other, FacebookwithZero", value)
    }

    @Test
    fun whenTrackersBlockedReturnOnlyTrackersWithDisplayName() {
        val trackers = listOf(
            TrackingEvent(
                documentUrl = "facebook.com",
                trackerUrl = "facebook.com",
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
                entity = TestingEntity("Facebook", "Facebook", 3.0),
                categories = null,
                surrogateId = null,
            ),
            TrackingEvent(
                documentUrl = "other.com",
                trackerUrl = "other.com",
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
                entity = TestingEntity("Other", "", 9.0),
                categories = null,
                surrogateId = null,
            ),
        )
        val site = site(events = trackers)

        val testee = OnboardingDaxDialogCta.DaxTrackersBlockedCta(
            mockOnboardingStore,
            mockAppInstallStore,
            site.orderedTrackerBlockedEntities(),
            mockSettingsDataStore,
            mockOnboardingDesignExperimentManager,
        )
        val value = testee.getTrackersDescription(mockActivity, site.orderedTrackerBlockedEntities())

        assertEquals("<b>Facebook</b>withZero", value)
    }

    @Test
    fun whenTrackersBlockedAndBBExperimentEnabledReturnOnlyTrackersWithDisplayName() {
        whenever(mockOnboardingDesignExperimentManager.isBbEnrolledAndEnabled()).thenReturn(true)

        val trackers = listOf(
            TrackingEvent(
                documentUrl = "facebook.com",
                trackerUrl = "facebook.com",
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
                entity = TestingEntity("Facebook", "Facebook", 3.0),
                categories = null,
                surrogateId = null,
            ),
            TrackingEvent(
                documentUrl = "other.com",
                trackerUrl = "other.com",
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
                entity = TestingEntity("Other", "", 9.0),
                categories = null,
                surrogateId = null,
            ),
        )
        val site = site(events = trackers)

        val testee = OnboardingDaxDialogCta.DaxTrackersBlockedCta(
            mockOnboardingStore,
            mockAppInstallStore,
            site.orderedTrackerBlockedEntities(),
            mockSettingsDataStore,
            mockOnboardingDesignExperimentManager,
        )
        val value = testee.getTrackersDescription(mockActivity, site.orderedTrackerBlockedEntities())

        assertEquals("FacebookwithZero", value)
    }

    @Test
    fun whenTrackersBlockedReturnOnlyTrackersBlocked() {
        val trackers = listOf(
            TrackingEvent(
                documentUrl = "facebook.com",
                trackerUrl = "facebook.com",
                status = TrackerStatus.ALLOWED,
                type = TrackerType.OTHER,
                entity = TestingEntity("Facebook", "Facebook", 3.0),
                categories = null,
                surrogateId = null,
            ),
            TrackingEvent(
                documentUrl = "other.com",
                trackerUrl = "other.com",
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
                entity = TestingEntity("Other", "Other", 9.0),
                categories = null,
                surrogateId = null,
            ),
        )
        val site = site(events = trackers)

        val testee = OnboardingDaxDialogCta.DaxTrackersBlockedCta(
            mockOnboardingStore,
            mockAppInstallStore,
            site.orderedTrackerBlockedEntities(),
            mockSettingsDataStore,
            mockOnboardingDesignExperimentManager,
        )
        val value = testee.getTrackersDescription(mockActivity, site.orderedTrackerBlockedEntities())

        assertEquals("<b>Other</b>withZero", value)
    }

    @Test
    fun whenTrackersBlockedAndBBExperimentEnabledReturnOnlyTrackersBlocked() {
        whenever(mockOnboardingDesignExperimentManager.isBbEnrolledAndEnabled()).thenReturn(true)

        val trackers = listOf(
            TrackingEvent(
                documentUrl = "facebook.com",
                trackerUrl = "facebook.com",
                status = TrackerStatus.ALLOWED,
                type = TrackerType.OTHER,
                entity = TestingEntity("Facebook", "Facebook", 3.0),
                categories = null,
                surrogateId = null,
            ),
            TrackingEvent(
                documentUrl = "other.com",
                trackerUrl = "other.com",
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
                entity = TestingEntity("Other", "Other", 9.0),
                categories = null,
                surrogateId = null,
            ),
        )
        val site = site(events = trackers)

        val testee = OnboardingDaxDialogCta.DaxTrackersBlockedCta(
            mockOnboardingStore,
            mockAppInstallStore,
            site.orderedTrackerBlockedEntities(),
            mockSettingsDataStore,
            mockOnboardingDesignExperimentManager,
        )
        val value = testee.getTrackersDescription(mockActivity, site.orderedTrackerBlockedEntities())

        assertEquals("OtherwithZero", value)
    }

    @Test
    fun whenMultipleTrackersFromSameNetworkBlockedReturnOnlyOneWithZeroString() {
        val trackers = listOf(
            TestingEntity("Facebook", "Facebook", 9.0),
            TestingEntity("Facebook", "Facebook", 9.0),
            TestingEntity("Facebook", "Facebook", 9.0),
        )

        val testee = OnboardingDaxDialogCta.DaxTrackersBlockedCta(
            mockOnboardingStore,
            mockAppInstallStore,
            trackers,
            mockSettingsDataStore,
            mockOnboardingDesignExperimentManager,
        )
        val value = testee.getTrackersDescription(mockActivity, trackers)

        assertEquals("<b>Facebook</b>withZero", value)
    }

    @Test
    fun whenMultipleTrackersFromSameNetworkBlockedAndBBExperimentEnabledReturnOnlyOneWithZeroString() {
        whenever(mockOnboardingDesignExperimentManager.isBbEnrolledAndEnabled()).thenReturn(true)

        val trackers = listOf(
            TestingEntity("Facebook", "Facebook", 9.0),
            TestingEntity("Facebook", "Facebook", 9.0),
            TestingEntity("Facebook", "Facebook", 9.0),
        )

        val testee = OnboardingDaxDialogCta.DaxTrackersBlockedCta(
            mockOnboardingStore,
            mockAppInstallStore,
            trackers,
            mockSettingsDataStore,
            mockOnboardingDesignExperimentManager,
        )
        val value = testee.getTrackersDescription(mockActivity, trackers)

        assertEquals("FacebookwithZero", value)
    }

    @Test
    fun whenTryClearDataCtaShownThenConcatenateJourneyStoredValueInPixel() {
        val existingJourney = "s:0-t:1"
        whenever(mockOnboardingStore.onboardingDialogJourney).thenReturn(existingJourney)
        whenever(mockAppInstallStore.installTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        val testee = OnboardingDaxDialogCta.DaxFireButtonCta(mockOnboardingStore, mockAppInstallStore, mockOnboardingDesignExperimentManager)
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
        entity: Entity? = null,
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
        return site
    }
}
