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

package com.duckduckgo.app.browser.tabs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.app.tabs.TabManagerFeatureFlags
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions

@RunWith(AndroidJUnit4::class)
class RealTabReuseDistanceReporterTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val pixel: Pixel = mock()
    private val tabManagerFeatureFlags = FakeFeatureToggleFactory.create(TabManagerFeatureFlags::class.java)

    private val testee = RealTabReuseDistanceReporter(
        pixel = pixel,
        tabManagerFeatureFlags = tabManagerFeatureFlags,
        appCoroutineScope = coroutineTestRule.testScope,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Before
    fun setup() {
        setToggles(parent = true, maxDistancePixel = true)
        testee.onTabCountChanged(3)
    }

    @Test
    fun whenNoTabWasReturnedToThenNoPixelIsFired() {
        activate("tab1", "tab2", "tab3")
        testee.onBrowserPaused()

        verifyNoInteractions(pixel)
    }

    @Test
    fun whenAlreadyActiveTabIsPlacedAgainThenNoPixelIsFired() {
        activate("tab1", "tab1", "tab1")
        testee.onBrowserPaused()

        verifyNoInteractions(pixel)
    }

    @Test
    fun whenReturnHappensThenPixelIsFiredOnlyOnPause() {
        activate("tab1", "tab2", "tab1")

        verifyNoInteractions(pixel)

        testee.onBrowserPaused()

        verifyBucketFired("1_3")
    }

    @Test
    fun whenThreeOtherTabsActivatedInBetweenThenBucketIsOneToThree() {
        returnToTargetAfter(otherTabs = 3)

        verifyBucketFired("1_3")
    }

    @Test
    fun whenFourOtherTabsActivatedInBetweenThenBucketIsFourToSix() {
        returnToTargetAfter(otherTabs = 4)

        verifyBucketFired("4_6")
    }

    @Test
    fun whenNineOtherTabsActivatedInBetweenThenBucketIsSevenToNine() {
        returnToTargetAfter(otherTabs = 9)

        verifyBucketFired("7_9")
    }

    @Test
    fun whenTenOtherTabsActivatedInBetweenThenBucketIsTenToTwelve() {
        returnToTargetAfter(otherTabs = 10)

        verifyBucketFired("10_12")
    }

    @Test
    fun whenTwelveOtherTabsActivatedInBetweenThenBucketIsTenToTwelve() {
        returnToTargetAfter(otherTabs = 12)

        verifyBucketFired("10_12")
    }

    @Test
    fun whenFifteenOtherTabsActivatedInBetweenThenBucketIsThirteenToFifteen() {
        returnToTargetAfter(otherTabs = 15)

        verifyBucketFired("13_15")
    }

    @Test
    fun whenSixteenOtherTabsActivatedInBetweenThenBucketIsSixteenPlus() {
        returnToTargetAfter(otherTabs = 16)

        verifyBucketFired("16_plus")
    }

    @Test
    fun whenSeveralReturnsHappenThenOnlyTheFurthestIsReported() {
        activate("near1", "near2", "near1")
        activate("target")
        activate(*otherTabs(10))
        testee.onTabActivated("target")
        activate("near3", "near4", "near3")
        testee.onBrowserPaused()

        verifyBucketFired("10_12")
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenOtherTabIsRepeatedlyPlacedInBetweenThenOnlyDistinctTabsCount() {
        activate("target")
        repeat(20) { testee.onTabActivated("other") }
        testee.onTabActivated("target")
        testee.onBrowserPaused()

        verifyBucketFired("1_3")
    }

    @Test
    fun whenTabsRemovedThenTheyNoLongerContributeToDistance() {
        activate("target")
        activate(*otherTabs(10))
        testee.onTabsRemoved(listOf("other0", "other1", "other2"))
        testee.onTabActivated("target")
        testee.onBrowserPaused()

        verifyBucketFired("7_9")
    }

    @Test
    fun whenReturningFromVeryFarBackThenBucketIsSixteenPlus() {
        returnToTargetAfter(otherTabs = 100)

        verifyBucketFired("16_plus")
    }

    @Test
    fun whenANewSessionHasNoReturnThenNoPixelIsFired() {
        returnToTargetAfter(otherTabs = 10)
        verifyBucketFired("10_12")

        testee.onBrowserPaused()
        testee.onBrowserPaused()

        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenANewSessionHasANearerReturnThenItsOwnMaxIsReported() {
        returnToTargetAfter(otherTabs = 10)
        verifyBucketFired("10_12")

        activate("near1", "near2", "near1")
        testee.onBrowserPaused()

        verifyBucketFired("1_3")
    }

    @Test
    fun whenANewSessionHasAFurtherReturnThenItsOwnMaxIsReported() {
        returnToTargetAfter(otherTabs = 10)
        verifyBucketFired("10_12")

        activate(*otherTabs(16))
        testee.onTabActivated("target")
        testee.onBrowserPaused()

        verifyBucketFired("16_plus")
    }

    @Test
    fun whenTabCountIsReportedThenItIsBucketed() {
        testee.onTabCountChanged(20)

        returnToTargetAfter(otherTabs = 4)

        verifyBucketFired(bucket = "4_6", tabCountBucket = "16_25")
    }

    @Test
    fun whenTabCountIsAboveEveryBucketThenBucketIsFiftyOnePlus() {
        testee.onTabCountChanged(120)

        returnToTargetAfter(otherTabs = 4)

        verifyBucketFired(bucket = "4_6", tabCountBucket = "51_plus")
    }

    @Test
    fun whenTabCountChangesBeforePauseThenTheLatestCountIsReported() {
        testee.onTabCountChanged(40)
        activate("target")
        activate(*otherTabs(4))
        testee.onTabActivated("target")
        testee.onTabCountChanged(8)
        testee.onBrowserPaused()

        verifyBucketFired(bucket = "4_6", tabCountBucket = "7_9")
    }

    @Test
    fun whenParentToggleDisabledThenNoPixelIsFired() {
        setToggles(parent = false, maxDistancePixel = true)

        returnToTargetAfter(otherTabs = 10)

        verifyNoInteractions(pixel)
    }

    @Test
    fun whenPixelToggleDisabledThenNoPixelIsFired() {
        setToggles(parent = true, maxDistancePixel = false)

        returnToTargetAfter(otherTabs = 10)

        verifyNoInteractions(pixel)
    }

    @Test
    fun whenPixelToggleDisabledThenActivationOrderIsStillRecorded() {
        setToggles(parent = true, maxDistancePixel = false)
        activate("target")
        activate(*otherTabs(10))
        verifyNoInteractions(pixel)

        setToggles(parent = true, maxDistancePixel = true)
        testee.onTabActivated("target")
        testee.onBrowserPaused()

        verifyBucketFired("10_12")
    }

    private fun setToggles(
        parent: Boolean,
        maxDistancePixel: Boolean,
    ) {
        tabManagerFeatureFlags.self().setRawStoredState(State(enable = parent))
        tabManagerFeatureFlags.tabMaxReuseDistancePixel().setRawStoredState(State(enable = maxDistancePixel))
    }

    private fun returnToTargetAfter(otherTabs: Int) {
        activate("target")
        activate(*otherTabs(otherTabs))
        testee.onTabActivated("target")
        testee.onBrowserPaused()
    }

    private fun verifyBucketFired(
        bucket: String,
        tabCountBucket: String = "1_3",
    ) {
        verify(pixel).fire(
            pixel = AppPixelName.TAB_MAX_REUSE_DISTANCE,
            parameters = mapOf("distance_bucket" to bucket, "tab_count_bucket" to tabCountBucket),
            encodedParameters = emptyMap(),
            type = Count,
        )
    }

    private fun activate(vararg tabIds: String) {
        tabIds.forEach { testee.onTabActivated(it) }
    }

    private fun otherTabs(count: Int): Array<String> = Array(count) { "other$it" }
}
