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

package com.duckduckgo.adblocking.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.adblocking.api.AdBlockingAnimation
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealAdBlockingOmnibarAnimationTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val statusChecker: AdBlockingStatusChecker = mock()

    private lateinit var testee: RealAdBlockingOmnibarAnimation

    @Before
    fun setup() {
        whenever(statusChecker.canInject()).thenReturn(true)
        testee = RealAdBlockingOmnibarAnimation(statusChecker, RealAdBlockingExtensionDomainMatcher())
    }

    @Test
    fun whenPageLoadedOnWatchVideoThenDoNotShow() = runTest {
        assertFalse(testee.getAnimation("https://www.youtube.com/watch?v=abc123", pageChanged = true) is AdBlockingAnimation.Show)
    }

    @Test @Ignore("Add back after fixing https://app.asana.com/1/137249556945/task/1216628472297441?focus=true")
    fun whenPageLoadedOnWatchVideoThenShow() = runTest {
        assertTrue(testee.getAnimation("https://www.youtube.com/watch?v=abc123", pageChanged = true) is AdBlockingAnimation.Show)
    }

    @Test @Ignore("Add back after fixing https://app.asana.com/1/137249556945/task/1216628472297441?focus=true")
    fun whenUrlChangedToSameVideoIdThenRetain() = runTest {
        assertTrue(testee.getAnimation("https://www.youtube.com/watch?v=abc123", pageChanged = true) is AdBlockingAnimation.Show)
        assertTrue(testee.getAnimation("https://www.youtube.com/watch?v=abc123", pageChanged = false) is AdBlockingAnimation.Retain)
    }

    @Test @Ignore("Add back after fixing https://app.asana.com/1/137249556945/task/1216628472297441?focus=true")
    fun whenPageReloadedOnSameVideoThenShowAgain() = runTest {
        assertTrue(testee.getAnimation("https://www.youtube.com/watch?v=abc123", pageChanged = true) is AdBlockingAnimation.Show)

        // A reload routes through pageChanged=true again → always re-animates.
        assertTrue(testee.getAnimation("https://www.youtube.com/watch?v=abc123", pageChanged = true) is AdBlockingAnimation.Show)
    }

    @Test @Ignore("Add back after fixing https://app.asana.com/1/137249556945/task/1216628472297441?focus=true")
    fun whenUrlChangedAToBToAThenShowOnReturn() = runTest {
        assertTrue(testee.getAnimation("https://www.youtube.com/watch?v=aaa", pageChanged = false) is AdBlockingAnimation.Show)
        assertTrue(testee.getAnimation("https://www.youtube.com/watch?v=bbb", pageChanged = false) is AdBlockingAnimation.Show)
        assertTrue(testee.getAnimation("https://www.youtube.com/watch?v=aaa", pageChanged = false) is AdBlockingAnimation.Show)
    }

    @Test @Ignore("Add back after fixing https://app.asana.com/1/137249556945/task/1216628472297441?focus=true")
    fun whenShortsLiveClipVideosThenShow() = runTest {
        assertTrue(testee.getAnimation("https://www.youtube.com/shorts/short1", pageChanged = false) is AdBlockingAnimation.Show)
        assertTrue(testee.getAnimation("https://www.youtube.com/live/live1", pageChanged = false) is AdBlockingAnimation.Show)
        assertTrue(testee.getAnimation("https://www.youtube.com/clip/clip1", pageChanged = false) is AdBlockingAnimation.Show)
    }

    @Test @Ignore("Add back after fixing https://app.asana.com/1/137249556945/task/1216628472297441?focus=true")
    fun whenWatchUrlHasEmptyVideoIdThenSkip() = runTest {
        assertTrue(testee.getAnimation("https://www.youtube.com/watch?v=", pageChanged = true) is AdBlockingAnimation.Skip)
    }

    @Test @Ignore("Add back after fixing https://app.asana.com/1/137249556945/task/1216628472297441?focus=true")
    fun whenNonVideoUrlThenSkip() = runTest {
        assertTrue(testee.getAnimation("https://www.youtube.com/results?search_query=cats", pageChanged = true) is AdBlockingAnimation.Skip)
    }

    @Test @Ignore("Add back after fixing https://app.asana.com/1/137249556945/task/1216628472297441?focus=true")
    fun whenNonYoutubeUrlThenSkip() = runTest {
        assertTrue(testee.getAnimation("https://www.example.com/watch?v=abc123", pageChanged = true) is AdBlockingAnimation.Skip)
    }

    @Test @Ignore("Add back after fixing https://app.asana.com/1/137249556945/task/1216628472297441?focus=true")
    fun whenFeatureToggleOffThenAlwaysSkip() = runTest {
        whenever(statusChecker.canInject()).thenReturn(false)
        assertTrue(testee.getAnimation("https://www.youtube.com/watch?v=abc123", pageChanged = true) is AdBlockingAnimation.Skip)
    }

    @Test @Ignore("Add back after fixing https://app.asana.com/1/137249556945/task/1216628472297441?focus=true")
    fun whenNonVideoAfterVideoThenReturningToVideoShows() = runTest {
        assertTrue(testee.getAnimation("https://www.youtube.com/watch?v=abc123", pageChanged = false) is AdBlockingAnimation.Show)

        // Navigating away clears state...
        assertTrue(testee.getAnimation("https://www.youtube.com/results?search_query=cats", pageChanged = false) is AdBlockingAnimation.Skip)

        // ...so returning to the same video animates again.
        assertTrue(testee.getAnimation("https://www.youtube.com/watch?v=abc123", pageChanged = false) is AdBlockingAnimation.Show)
    }

    @Test
    fun whenNonVideoUrlThenCanInjectNotChecked() = runTest {
        // The cheap video check gates the expensive feature-toggle read: non-YouTube urls must skip it.
        testee.getAnimation("https://www.example.com/watch?v=abc123", pageChanged = true)
        verify(statusChecker, never()).canInject()
    }
}
