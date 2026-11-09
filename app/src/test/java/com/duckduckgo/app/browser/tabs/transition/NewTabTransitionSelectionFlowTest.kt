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
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.BrowserViewModel
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts
import com.duckduckgo.app.browser.tabs.transition.NewTabOrigin.BELOW_CENTER
import com.duckduckgo.app.browser.tabs.transition.NewTabOrigin.RIGHT_OF_CENTER
import com.duckduckgo.app.global.rating.AppEnjoymentPromptEmitter
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

/**
 * Drives the coordinator from the real tab list and selected-index flows, with their debounce and the intermediate indices
 * they emit when adding a tab deletes a blank one. The two calls mirror `BrowserActivity.onTabsUpdated` and
 * `onMoveToTabRequested` with swiping on.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class NewTabTransitionSelectionFlowTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val root = FrameLayout(context)
    private val cover = ImageView(context).apply { visibility = View.GONE }
    private val container = FrameLayout(context)
    private val state = RealNewTabTransitionState()
    private val pendingTransitions = RealPendingNewTabTransitions(mock(), coroutineRule.testScope, coroutineRule.testDispatcherProvider)
    private val testee = NewTabTransitionCoordinator(NewTabTransitionPlayer(cover, state), pendingTransitions, state) {}

    // LiveData-backed, as TabDataRepository's flows are: a new collector gets the cached value first.
    private val tabs = MutableLiveData<List<TabEntity>>(emptyList())
    private val selectedTab = MutableLiveData<TabEntity?>(null)
    private val selections = mutableListOf<String>()
    private val runningWhenSelected = mutableListOf<Boolean>()
    private val jobs = mutableListOf<Job>()

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

    @After
    fun tearDown() {
        jobs.forEach { it.cancel() }
        testee.cancel()
    }

    @Test
    fun blankBeforeSelectedTabIsDeleted() {
        startBrowsing(listOf(BLANK, SOURCE), selected = SOURCE)

        openNewTab(listOf(SOURCE, ADDED))

        assertEquals(listOf("source", "source", "added"), selections)
        assertEquals(listOf(false, true, true), runningWhenSelected)
        assertGrowing()
    }

    @Test
    fun onlyTabIsBlankAndDeleted() {
        startBrowsing(listOf(BLANK), selected = BLANK)

        openNewTab(listOf(ADDED))

        assertEquals(listOf("blank", "added"), selections)
        assertEquals(listOf(false, true), runningWhenSelected)
        assertGrowing()
    }

    @Test
    fun blankAmongSeveralTabsIsDeleted() {
        startBrowsing(listOf(OTHER, BLANK, SOURCE), selected = SOURCE)

        openNewTab(listOf(OTHER, SOURCE, ADDED))

        assertEquals(listOf("source", "source", "added"), selections)
        assertEquals(listOf(false, true, true), runningWhenSelected)
        assertGrowing()
    }

    @Test
    fun tabManagerReturnWhileTheOldSelectionIsStillCachedGrowsTheNewTab() {
        startBrowsing(listOf(SOURCE), selected = SOURCE)
        pendingTransitions.register(
            ADDED.tabId,
            PendingNewTabTransition(
                CompletableDeferred(readyPicture()),
                NewTabTransitionConfig.DEFAULT.motionFor(BELOW_CENTER),
                fromTabManager = true,
            ),
        )
        testee.onBrowserStarting(container)

        // The tab list arrives first; the selection still replays the old tab until the new one propagates.
        tabs.value = listOf(SOURCE, ADDED)
        advance(DEBOUNCE_MS)
        selectedTab.value = ADDED
        advance(DEBOUNCE_MS)

        assertEquals(listOf("source", "source", "added"), selections)
        assertEquals(listOf(false, true, true), runningWhenSelected)
        assertGrowing()
    }

    @Test
    fun pictureNotReadyWhenTheListFirstHasTheTabIsNotAnimated() {
        startBrowsing(listOf(BLANK, SOURCE), selected = SOURCE)
        val picture = CompletableDeferred<Bitmap?>()

        openNewTab(listOf(SOURCE, ADDED), picture) {
            picture.complete(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888))
        }

        assertEquals("added", selections.last())
        assertFalse(state.isRunning.value)
        assertEquals(1f, container.scaleX, DELTA)
        assertEquals(1f, container.alpha, DELTA)
    }

    private fun startBrowsing(
        initialTabs: List<TabEntity>,
        selected: TabEntity,
    ) {
        tabs.value = initialTabs
        selectedTab.value = selected
        val viewModel = viewModel()
        var tabIds = emptyList<String>()
        jobs += coroutineRule.testScope.launch {
            viewModel.tabsFlow.collect { updated ->
                tabIds = updated.map { it.tabId }
                testee.onTabsChanged(tabIds, container)
            }
        }
        jobs += coroutineRule.testScope.launch {
            viewModel.selectedTabIndex.collect { index ->
                val tabId = tabIds[index]
                selections += tabId
                runningWhenSelected += state.isRunning.value
                testee.onTabShowing(tabId, container)
            }
        }
        advance(DEBOUNCE_MS)
    }

    // Registered when the add returns, then the repository's tab list and (debounced) selection both change.
    private fun openNewTab(
        updatedTabs: List<TabEntity>,
        picture: CompletableDeferred<Bitmap?> = CompletableDeferred(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)),
        afterListUpdate: () -> Unit = {},
    ) {
        pendingTransitions.register(ADDED.tabId, PendingNewTabTransition(picture, NewTabTransitionConfig.DEFAULT.motionFor(RIGHT_OF_CENTER)))
        selectedTab.value = ADDED
        tabs.value = updatedTabs
        coroutineRule.testScope.runCurrent()
        afterListUpdate()
        advance(DEBOUNCE_MS)
    }

    private fun readyPicture() = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)

    private fun assertGrowing() {
        assertTrue(state.isRunning.value)
        assertEquals(0.8f, container.scaleX, DELTA)
    }

    private fun advance(millis: Long) {
        coroutineRule.testScope.advanceTimeBy(millis)
        coroutineRule.testScope.runCurrent()
    }

    private fun viewModel(): BrowserViewModel {
        val repository: TabRepository = mock()
        whenever(repository.flowTabs).thenReturn(tabs.asFlow())
        whenever(repository.flowSelectedTab).thenReturn(selectedTab.asFlow().distinctUntilChanged())
        whenever(repository.liveTabs).thenReturn(MutableLiveData())
        whenever(repository.liveSelectedTab).thenReturn(MutableLiveData())
        val prompts: AppEnjoymentPromptEmitter = mock()
        whenever(prompts.promptType).thenReturn(MutableLiveData())
        val additionalPrompts: AdditionalDefaultBrowserPrompts = mock()
        whenever(additionalPrompts.commands).thenReturn(emptyFlow())
        val browserModeStateHolder: BrowserModeStateHolder = mock()
        whenever(browserModeStateHolder.currentMode).thenReturn(MutableStateFlow(BrowserMode.REGULAR))
        return BrowserViewModel(
            repository,
            mock(),
            mock(),
            prompts,
            mock(),
            mock(),
            coroutineRule.testDispatcherProvider,
            mock(),
            mock(),
            additionalPrompts,
            mock(),
            mock(),
            FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java),
            browserModeStateHolder,
            mock(),
            BrowserMode.REGULAR,
            mock(),
        )
    }

    private companion object {
        const val WIDTH = 100
        const val HEIGHT = 200
        const val DELTA = 0.0001f
        const val DEBOUNCE_MS = 101L
        val BLANK = TabEntity(tabId = "blank")
        val SOURCE = TabEntity(tabId = "source", url = "https://example.com")
        val OTHER = TabEntity(tabId = "other", url = "https://example.com/other")
        val ADDED = TabEntity(tabId = "added", url = "https://example.com/new")
    }
}
