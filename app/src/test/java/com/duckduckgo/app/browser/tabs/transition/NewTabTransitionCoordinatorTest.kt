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

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.tabs.transition.NewTabOrigin.RIGHT_OF_CENTER
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class NewTabTransitionCoordinatorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val root = FrameLayout(context)
    private val cover = ImageView(context).apply { visibility = View.GONE }
    private val container = FrameLayout(context)
    private val state = RealNewTabTransitionState()
    private val pendingTransitions = RealPendingNewTabTransitions(
        FixedTimeProvider(),
        coroutineRule.testScope,
        coroutineRule.testDispatcherProvider,
    )
    private val motion = NewTabTransitionConfig.DEFAULT.motionFor(RIGHT_OF_CENTER)
    private val runningChanges = mutableListOf<Boolean>()

    private val testee = NewTabTransitionCoordinator(NewTabTransitionPlayer(cover, state), pendingTransitions, state) { runningChanges += it }

    @Before
    fun setUp() {
        root.addView(cover, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(container, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, WIDTH, HEIGHT)
    }

    @Test
    fun registeredTabWithReadyPicturePlays() {
        register("a")

        testee.onTabShowing("a", container)

        assertPlaying()
        assertEquals(listOf(true), runningChanges)
    }

    @Test
    fun registeredTabWithPendingPictureShowsAtOnce() {
        register("a", CompletableDeferred())

        testee.onTabShowing("a", container)

        assertNotPlaying()
        assertTrue(runningChanges.isEmpty())
    }

    @Test
    fun replacementStaysRunningUntilItEnds() {
        register("a")
        testee.onTabShowing("a", container)
        register("b")

        testee.onTabShowing("b", container)
        assertEquals(listOf(true), runningChanges)

        testee.cancel()
        assertEquals(listOf(true, false), runningChanges)
    }

    @Test
    fun otherTabWithPendingPictureEndsRunningTransition() {
        register("a")
        testee.onTabShowing("a", container)
        register("b", CompletableDeferred())

        testee.onTabShowing("b", container)

        assertNotPlaying()
        assertEquals(listOf(true, false), runningChanges)
    }

    @Test
    fun otherTabWithFailedPictureEndsRunningTransition() {
        register("a")
        testee.onTabShowing("a", container)
        assertPlaying()
        register("b", failedPicture())

        testee.onTabShowing("b", container)

        assertNotPlaying()
    }

    @Test
    fun unregisteredTabEndsRunningTransition() {
        register("a")
        testee.onTabShowing("a", container)

        testee.onTabShowing("b", container)

        assertNotPlaying()
        assertEquals(listOf(true, false), runningChanges)
    }

    @Test
    fun sameTabShownAgainKeepsPlaying() {
        register("a")
        testee.onTabShowing("a", container)

        testee.onTabShowing("a", container)

        assertPlaying()
    }

    @Test
    fun listedTabIsCoveredUntilSelectionReachesIt() {
        testee.onTabShowing("source", container)
        register("added")

        testee.onTabsChanged(listOf("source", "added"), container)
        assertCovered()

        // The selection can pass another tab first while the list shifts under it.
        testee.onTabShowing("source", container)
        assertCovered()

        testee.onTabShowing("added", container)
        assertPlaying()
        assertEquals(listOf(true), runningChanges)
    }

    @Test
    fun listedTabWithPendingPictureIsNotAnimatedLater() {
        val picture = CompletableDeferred<Bitmap?>()
        register("added", picture)

        testee.onTabsChanged(listOf("source", "added"), container)
        picture.complete(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888))
        testee.onTabShowing("added", container)

        assertNotPlaying()
        assertTrue(runningChanges.isEmpty())
    }

    @Test
    fun listedTabWhosePictureArrivesWhileHiddenIsCoveredThenPlays() {
        testee.onTabShowing("source", container)
        val picture = CompletableDeferred<Bitmap?>()
        register("added", picture)
        testee.onTabsChanged(listOf("source", "added"), container)
        assertNotPlaying()

        picture.complete(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888))
        testee.onTabRegistered("added", listedTabIds = listOf("source", "added"), visibleTabId = "source", container)
        assertCovered()

        testee.onTabShowing("added", container)
        assertPlaying()
    }

    @Test
    fun listedTabWhosePictureArrivesOnceItIsOnTheVisiblePageIsNotAnimated() {
        testee.onTabShowing("source", container)
        val picture = CompletableDeferred<Bitmap?>()
        register("added", picture)
        testee.onTabsChanged(listOf("source", "added"), container)

        picture.complete(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888))
        testee.onTabRegistered("added", listedTabIds = listOf("source", "added"), visibleTabId = "added", container)
        testee.onTabShowing("added", container)

        assertNotPlaying()
        assertTrue(runningChanges.isEmpty())
    }

    @Test
    fun anotherSelectionWhileAwaitingPictureDropsTheTransition() {
        testee.onTabShowing("source", container)
        val picture = CompletableDeferred<Bitmap?>()
        register("added", picture)
        testee.onTabsChanged(listOf("source", "added", "other"), container)

        testee.onTabShowing("other", container)
        picture.complete(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888))
        testee.onTabRegistered("added", listedTabIds = listOf("source", "added", "other"), visibleTabId = "other", container)

        assertNotPlaying()
        assertTrue(runningChanges.isEmpty())
        assertEquals(null, pendingTransitions.peek("added"))
    }

    @Test
    fun sourceReplayWhileAwaitingPictureKeepsTheTransition() {
        testee.onTabShowing("source", container)
        val picture = CompletableDeferred<Bitmap?>()
        register("added", picture)
        testee.onTabsChanged(listOf("source", "added"), container)

        testee.onTabShowing("source", container)
        picture.complete(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888))
        testee.onTabRegistered("added", listedTabIds = listOf("source", "added"), visibleTabId = "source", container)

        assertCovered()
    }

    @Test
    fun listedTabWithFailedPictureIsNotAnimated() {
        register("added", failedPicture())

        testee.onTabsChanged(listOf("source", "added"), container)
        testee.onTabShowing("added", container)

        assertNotPlaying()
        assertTrue(runningChanges.isEmpty())
    }

    @Test
    fun coveredTabThatCannotPlayWhenSelectedShowsAtOnce() {
        register("added")
        testee.onTabsChanged(listOf("added"), container)
        pendingTransitions.clear()

        testee.onTabShowing("added", container)

        assertNotPlaying()
        assertEquals(listOf(true, false), runningChanges)
    }

    @Test
    fun coverTimeoutDropsTheTransition() {
        register("added")
        testee.onTabsChanged(listOf("added"), container)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1_000))
        testee.onTabShowing("added", container)

        assertNotPlaying()
        assertEquals(listOf(true, false), runningChanges)
    }

    @Test
    fun tabsWithoutRegisteredTabDoNothing() {
        register("added")

        testee.onTabsChanged(listOf("source"), container)

        assertNotPlaying()
        assertTrue(runningChanges.isEmpty())
    }

    @Test
    fun shownTabIsKnownBeforeTheCancelledTransitionReleasesTheKeyboard() {
        register("a")
        testee.onTabShowing("a", container)
        var shownTabAtIdle: String? = null
        coroutineRule.testScope.launch {
            state.isRunning.first { !it }
            shownTabAtIdle = state.shownTabId
        }

        testee.onTabShowing("b", container)

        assertEquals("b", shownTabAtIdle)
    }

    @Test
    fun coverLeavesTheShownTabUntilItsTabPlays() {
        testee.onTabShowing("source", container)
        register("added")
        testee.onTabsChanged(listOf("source", "added"), container)

        testee.onTabShowing("source", container)
        assertEquals("source", state.shownTabId)

        testee.onTabShowing("added", container)
        assertEquals("added", state.shownTabId)
    }

    @Test
    fun coverWithoutAKnownShownTabKeepsItselfForOtherTabs() {
        register("added")
        testee.onTabsChanged(listOf("source", "added"), container)

        testee.onTabShowing("source", container)

        assertCovered()
    }

    @Test
    fun unanimatedSelectionDuringCoverEndsItAtOnce() {
        testee.onTabShowing("source", container)
        register("added")
        testee.onTabsChanged(listOf("source", "added", "other"), container)

        testee.onTabShowing("other", container)

        assertNotPlaying()
        assertEquals("other", state.shownTabId)
        assertEquals(listOf(true, false), runningChanges)
    }

    @Test
    fun replacementWithPendingPictureEndsSupersededCoverWhenItShows() {
        assertReplacementEndsSupersededCover(CompletableDeferred())
    }

    @Test
    fun replacementWithFailedPictureEndsSupersededCoverWhenItShows() {
        assertReplacementEndsSupersededCover(failedPicture())
    }

    @Test
    fun supersededCoverEndsEvenForTheTabItStartedFrom() {
        testee.onTabShowing("source", container)
        register("a")
        testee.onTabsChanged(listOf("source", "a"), container)
        register("b", CompletableDeferred())
        testee.onTabsChanged(listOf("source", "a", "b"), container)

        testee.onTabShowing("source", container)

        assertNotPlaying()
    }

    @Test
    fun readyReplacementTakesOverTheCover() {
        testee.onTabShowing("source", container)
        register("a")
        testee.onTabsChanged(listOf("source", "a"), container)
        register("b")
        testee.onTabsChanged(listOf("source", "a", "b"), container)

        testee.onTabShowing("source", container)
        assertCovered()

        testee.onTabShowing("b", container)
        assertPlaying()
        assertEquals(listOf(true), runningChanges)
    }

    @Test
    fun registrationBeforeTheListLeavesTheCoverToIt() {
        register("added")

        testee.onTabRegistered("added", listedTabIds = listOf("source"), visibleTabId = "source", container)

        assertNotPlaying()
        testee.onTabsChanged(listOf("source", "added"), container)
        assertCovered()
    }

    @Test
    fun registrationAfterTheListWhileTheTabIsHiddenCoversIt() {
        testee.onTabShowing("source", container)
        register("added")

        testee.onTabRegistered("added", listedTabIds = listOf("source", "added"), visibleTabId = "source", container)

        assertCovered()
    }

    @Test
    fun registrationAfterTheTabWasShownDropsTheTransition() {
        testee.onTabShowing("added", container)
        register("added")

        testee.onTabRegistered("added", listedTabIds = listOf("added"), visibleTabId = "added", container)

        assertNotPlaying()
        assertTrue(runningChanges.isEmpty())
        testee.onTabShowing("added", container)
        assertNotPlaying()
    }

    @Test
    fun registrationWhileTheTabIsOnTheVisiblePageDropsTheTransition() {
        testee.onTabShowing("blank", container)
        register("added")

        // Adding the tab deleted the blank tab on screen, so the pager already shows the new tab at its position.
        testee.onTabRegistered("added", listedTabIds = listOf("added"), visibleTabId = "added", container)

        assertNotPlaying()
        testee.onTabShowing("added", container)
        assertNotPlaying()
    }

    @Test
    fun tabManagerTransitionCoversTheBrowserUntilItsTabShows() {
        testee.onTabShowing("source", container)
        register("added", fromTabManager = true)

        testee.onBrowserStarting(container)
        assertCovered()

        testee.onTabShowing("added", container)
        assertPlaying()
        assertEquals(listOf(true), runningChanges)
    }

    @Test
    fun tabManagerTransitionForTheShownBlankTabPlaysWhenItShowsAgain() {
        testee.onTabShowing("blank", container)
        register("blank", fromTabManager = true)

        testee.onBrowserStarting(container)
        testee.onTabShowing("blank", container)

        assertPlaying()
    }

    @Test
    fun listUpdateDuringTabManagerCoverKeepsIt() {
        testee.onTabShowing("source", container)
        register("added", fromTabManager = true)
        testee.onBrowserStarting(container)

        testee.onTabsChanged(listOf("source", "added"), container)

        assertCovered()
        assertEquals(listOf(true), runningChanges)
    }

    @Test
    fun tabManagerCoverWaitsOutTheTabTheBrowserLeftOn() {
        // The selection can replay the tab the browser left on until the tab manager's new selection propagates.
        testee.onTabShowing("source", container)
        register("added", fromTabManager = true)
        testee.onBrowserStarting(container)

        testee.onTabShowing("source", container)
        assertCovered()

        testee.onTabShowing("added", container)
        assertPlaying()
        assertEquals(listOf(true), runningChanges)
    }

    @Test
    fun anotherTabShownDuringTabManagerCoverEndsIt() {
        testee.onTabShowing("source", container)
        register("added", fromTabManager = true)
        testee.onBrowserStarting(container)

        testee.onTabShowing("other", container)

        assertNotPlaying()
        assertEquals("other", state.shownTabId)
    }

    @Test
    fun isShowingOnlyTheTabLastShown() {
        testee.onBrowserResumed()
        testee.onTabShowing("a", container)
        testee.onTabShowing("b", container)

        assertTrue(testee.isShowing("b"))
        assertFalse(testee.isShowing("a"))
        assertFalse(testee.isShowing(null))
    }

    @Test
    fun isShowingNothingBeforeAnyTabShows() {
        testee.onBrowserResumed()

        assertFalse(testee.isShowing("a"))
        assertFalse(testee.isShowing(null))
    }

    @Test
    fun isShowingNothingBeforeTheBrowserResumes() {
        testee.onTabShowing("a", container)

        assertFalse(testee.isShowing("a"))
    }

    @Test
    fun isShowingNothingFromTheBrowserStoppingUntilItResumes() {
        testee.onBrowserResumed()
        testee.onTabShowing("a", container)

        testee.onBrowserStopped()
        assertFalse(testee.isShowing("a"))

        testee.onBrowserResumed()
        assertTrue(testee.isShowing("a"))
    }

    @Test
    fun browserTransitionDoesNotCoverWhenTheBrowserStarts() {
        register("added")

        testee.onBrowserStarting(container)

        assertNotPlaying()
    }

    @Test
    fun tabManagerTransitionWithoutPictureIsDroppedWhenTheBrowserStarts() {
        register("added", failedPicture(), fromTabManager = true)

        testee.onBrowserStarting(container)
        testee.onTabShowing("added", container)

        assertNotPlaying()
    }

    private fun assertReplacementEndsSupersededCover(replacementPicture: CompletableDeferred<Bitmap?>) {
        testee.onTabShowing("source", container)
        register("a")
        testee.onTabsChanged(listOf("source", "a"), container)
        assertCovered()
        register("b", replacementPicture)
        testee.onTabsChanged(listOf("source", "a", "b"), container)

        testee.onTabShowing("b", container)

        assertNotPlaying()
        assertEquals("b", state.shownTabId)
        assertEquals(listOf(true, false), runningChanges)
    }

    @Test
    fun cancelKeepsTheShownTab() {
        register("a")
        testee.onTabShowing("a", container)

        testee.cancel()

        assertEquals("a", state.shownTabId)
    }

    private fun register(
        tabId: String,
        picture: CompletableDeferred<Bitmap?> = CompletableDeferred(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)),
        fromTabManager: Boolean = false,
    ) {
        pendingTransitions.register(tabId, PendingNewTabTransition(picture, motion, fromTabManager))
    }

    private fun assertPlaying() {
        assertTrue(state.isRunning.value)
        assertEquals(0.8f, container.scaleX, DELTA)
    }

    // CompletableDeferred(null) would pick the parent-job overload and stay pending.
    private fun failedPicture() = CompletableDeferred<Bitmap?>().apply { complete(null) }

    private fun assertCovered() {
        assertTrue(state.isRunning.value)
        assertEquals(View.VISIBLE, cover.visibility)
        assertEquals(0f, container.alpha, DELTA)
        assertEquals(1f, container.scaleX, DELTA)
    }

    private fun assertNotPlaying() {
        assertFalse(state.isRunning.value)
        assertEquals(1f, container.scaleX, DELTA)
        assertEquals(1f, container.alpha, DELTA)
        assertEquals(View.GONE, cover.visibility)
    }

    private class FixedTimeProvider : CurrentTimeProvider {
        override fun elapsedRealtime(): Long = 0L

        override fun currentTimeMillis(): Long = 0L

        override fun localDateTimeNow(): LocalDateTime = LocalDateTime.now()
    }

    private companion object {
        const val WIDTH = 100
        const val HEIGHT = 200
        const val DELTA = 0.0001f
    }
}
