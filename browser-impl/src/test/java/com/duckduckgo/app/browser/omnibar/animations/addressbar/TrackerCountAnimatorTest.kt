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

package com.duckduckgo.app.browser.omnibar.animations.addressbar

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.ui.view.text.DaxTextView
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class TrackerCountAnimatorTest {

    private lateinit var testee: TrackerCountAnimator
    private lateinit var context: Context
    private lateinit var mockTextView: DaxTextView

    @Before
    fun setup() {
        testee = TrackerCountAnimator()
        context = InstrumentationRegistry.getInstrumentation().targetContext
        mockTextView = mock()
    }

    @Test
    fun whenGetTrackerAnimationEndCountWithZeroThenReturnsZero() {
        val result = testee.getTrackerAnimationEndCount(0)
        assertEquals(0, result)
    }

    @Test
    fun whenGetTrackerAnimationEndCountWithSmallNumberThenReturnsSameNumber() {
        val result = testee.getTrackerAnimationEndCount(10)
        assertEquals(10, result)
    }

    @Test
    fun whenGetTrackerAnimationEndCountAtMaxLimitThenReturnsMaxLimit() {
        val result = testee.getTrackerAnimationEndCount(9999)
        assertEquals(9999, result)
    }

    @Test
    fun whenGetTrackerAnimationEndCountAboveMaxLimitThenReturnsMaxLimit() {
        val result = testee.getTrackerAnimationEndCount(10000)
        assertEquals(9999, result)
    }

    @Test
    fun whenGetTrackerAnimationStartCountWithZeroThenReturnsZero() {
        val result = testee.getTrackerAnimationStartCount(0)
        assertEquals(0, result)
    }

    @Test
    fun whenGetTrackerAnimationStartCountWithSmallNumberBelowThresholdThenReturns75Percent() {
        // For numbers below 40, use 75% threshold
        val result = testee.getTrackerAnimationStartCount(20)
        assertEquals(15, result) // 20 * 0.75 = 15
    }

    @Test
    fun whenGetTrackerAnimationStartCountAt39ThenReturns75Percent() {
        // Just below the upper threshold (40)
        val result = testee.getTrackerAnimationStartCount(39)
        assertEquals(29, result) // 39 * 0.75 = 29.25, rounded to 29
    }

    @Test
    fun whenGetTrackerAnimationStartCountAt40ThenReturns85Percent() {
        // At the upper threshold (40)
        val result = testee.getTrackerAnimationStartCount(40)
        assertEquals(34, result) // 40 * 0.85 = 34
    }

    @Test
    fun whenGetTrackerAnimationStartCountAboveThresholdThenReturns85Percent() {
        // For numbers >= 40, use 85% threshold
        val result = testee.getTrackerAnimationStartCount(100)
        assertEquals(85, result) // 100 * 0.85 = 85
    }

    @Test
    fun whenGetTrackerAnimationStartCountWithLargeNumberThenReturns85Percent() {
        val result = testee.getTrackerAnimationStartCount(1000)
        assertEquals(850, result) // 1000 * 0.85 = 850
    }

    @Test
    fun whenGetTrackerAnimationStartCountAtMaxLimitThenReturns85PercentOfMaxLimit() {
        val result = testee.getTrackerAnimationStartCount(9999)
        assertEquals(8499, result) // 9999 * 0.85 = 8499.15, rounded to 8499
    }

    @Test
    fun whenGetTrackerAnimationStartCountAboveMaxLimitThenCapsAtMaxAndReturns85Percent() {
        // Should cap at 9999 first, then calculate 85%
        val result = testee.getTrackerAnimationStartCount(15000)
        assertEquals(8499, result) // 9999 * 0.85 = 8499.15, rounded to 8499
    }

    @Test
    fun whenGetTrackerAnimationStartCountAt4ThenReturns75Percent() {
        // Below minimum animation trigger threshold (5)
        val result = testee.getTrackerAnimationStartCount(4)
        assertEquals(3, result) // 4 * 0.75 = 3
    }

    @Test
    fun whenGetTrackerAnimationStartCountAt5ThenReturns75Percent() {
        // At minimum animation trigger threshold (5)
        val result = testee.getTrackerAnimationStartCount(5)
        assertEquals(4, result) // 5 * 0.75 = 3.75, rounded to 4
    }

    @Test
    fun whenGetTrackerAnimationEndCountAt4ThenReturns4() {
        val result = testee.getTrackerAnimationEndCount(4)
        assertEquals(4, result)
    }

    @Test
    fun whenGetTrackerAnimationEndCountAt5ThenReturns5() {
        val result = testee.getTrackerAnimationEndCount(5)
        assertEquals(5, result)
    }

    @Test
    fun whenGetTrackerAnimationStartCountWith8ThenReturns6() {
        // 8 * 0.75 = 6
        val result = testee.getTrackerAnimationStartCount(8)
        assertEquals(6, result)
    }

    @Test
    fun whenGetTrackerAnimationStartCountWith10ThenReturns8() {
        // 10 * 0.75 = 7.5, should round to 8
        val result = testee.getTrackerAnimationStartCount(10)
        assertEquals(8, result)
    }

    @Test
    fun whenGetTrackerAnimationStartCountWith50ThenReturns43() {
        // 50 * 0.85 = 42.5, should round to 43
        val result = testee.getTrackerAnimationStartCount(50)
        assertEquals(43, result)
    }

    @Test
    fun whenGetTrackerAnimationStartCountWith100ThenReturns85() {
        // 100 * 0.85 = 85
        val result = testee.getTrackerAnimationStartCount(100)
        assertEquals(85, result)
    }
}
