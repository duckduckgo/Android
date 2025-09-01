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

package com.duckduckgo.app.tabs.ui

import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_INFO_PANEL_DISMISSED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_INFO_PANEL_IMPRESSIONS
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_INFO_PANEL_TAPPED
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.trackerdetection.api.WebTrackersBlockedAppRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TabSwitcherAnimationInfoPanelPixelsTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var mockWebTrackersBlockedAppRepository: WebTrackersBlockedAppRepository

    @Mock
    private lateinit var mockDispatcherProvider: DispatcherProvider

    private lateinit var testee: TabSwitcherAnimationInfoPanelPixels

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)
        whenever(mockDispatcherProvider.io()).thenReturn(coroutinesTestRule.testDispatcherProvider.io())

        testee = TabSwitcherAnimationInfoPanelPixelsImpl(
            pixel = mockPixel,
            webTrackersBlockedAppRepository = mockWebTrackersBlockedAppRepository,
            dispatcherProvider = mockDispatcherProvider,
        )
    }

    @Test
    fun whenFireInfoPanelTappedThenCorrectPixelIsFired() {
        testee.fireInfoPanelTapped()

        verify(mockPixel).fire(pixel = TAB_MANAGER_INFO_PANEL_TAPPED)
    }

    @Test
    fun whenFireInfoPanelImpressionThenCorrectPixelIsFired() {
        testee.fireInfoPanelImpression()

        verify(mockPixel).fire(pixel = TAB_MANAGER_INFO_PANEL_IMPRESSIONS)
    }

    @Test
    fun whenFireInfoPanelDismissedWithZeroTrackerCountThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(0)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "0"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithSingleDigitTrackerCountThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(5)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "1"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCountAtLowerBoundaryThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(1)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "1"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCountAtUpperBoundaryThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(9)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "1"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCount10ThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(10)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "10"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCount24ThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(24)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "10"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCount25ThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(25)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "40"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCount49ThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(49)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "40"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCount50ThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(50)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "50"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCount74ThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(74)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "50"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCount75ThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(75)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "75"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCount99ThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(99)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "75"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCount100ThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(100)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "100"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCount149ThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(149)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "100"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCount150ThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(150)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "150"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCount199ThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(199)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "150"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCount200ThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(200)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "200"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCount499ThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(499)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "200"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCount500ThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(500)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "500"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithTrackerCount1000ThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(1000)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "500"),
        )
    }

    @Test
    fun whenFireInfoPanelDismissedWithVeryHighTrackerCountThenPixelFiredWithCorrectBucket() = runTest {
        whenever(mockWebTrackersBlockedAppRepository.getTrackerCountForLast7Days()).thenReturn(Int.MAX_VALUE)

        testee.fireInfoPanelDismissed()

        verify(mockPixel).fire(
            pixel = TAB_MANAGER_INFO_PANEL_DISMISSED,
            parameters = mapOf("trackerCount" to "500"),
        )
    }
}
