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

package com.duckduckgo.app.browser.tabs.transition

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabPagerSwipeGateTest {

    private val tabPager = ViewPager2(ApplicationProvider.getApplicationContext())
    private val testee = TabPagerSwipeGate(tabPager)

    @Test
    fun transitionPausesSwipingAndItsEndResumesIt() {
        testee.pausedByTransition = true
        assertFalse(tabPager.isUserInputEnabled)

        testee.pausedByTransition = false
        assertTrue(tabPager.isUserInputEnabled)
    }

    @Test
    fun browserAllowingSwipingDuringTransitionDoesNotResumeIt() {
        testee.allowedByBrowser = false
        testee.pausedByTransition = true

        testee.allowedByBrowser = true
        assertFalse(tabPager.isUserInputEnabled)

        testee.pausedByTransition = false
        assertTrue(tabPager.isUserInputEnabled)
    }

    @Test
    fun browserDisallowingSwipingDuringTransitionKeepsItOffAfterward() {
        testee.pausedByTransition = true

        testee.allowedByBrowser = false
        testee.pausedByTransition = false

        assertFalse(tabPager.isUserInputEnabled)
    }
}
