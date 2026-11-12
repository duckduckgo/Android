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

package com.duckduckgo.app.browser

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import com.duckduckgo.app.browser.databinding.ActivityBrowserBinding
import com.duckduckgo.app.browser.tabs.TabManager
import com.duckduckgo.app.browser.tabs.TabManager.TabModel
import com.duckduckgo.app.browser.tabs.adapter.TabPagerAdapter
import com.duckduckgo.app.browser.tabs.transition.NewTabOrigin
import com.duckduckgo.app.browser.tabs.transition.NewTabTransitionConfig
import com.duckduckgo.app.browser.tabs.transition.NewTabTransitionCoordinator
import com.duckduckgo.app.browser.tabs.transition.NewTabTransitionPlayer
import com.duckduckgo.app.browser.tabs.transition.PendingNewTabTransition
import com.duckduckgo.app.browser.tabs.transition.RealNewTabTransitionState
import com.duckduckgo.app.browser.tabs.transition.RealPendingNewTabTransitions
import com.duckduckgo.app.pixels.BrowserModeSwitchSource
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.InputMode
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.CALLS_REAL_METHODS
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Runs BrowserActivity's real new tab code on a partial mock, since building the whole activity isn't practical here. Its tab
 * pager, adapter, view model and coordinator are swapped in, and the window has nothing drawn, so pictures come back empty.
 * Each entry point in the browser opens a new tab through one of the methods called here.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class BrowserActivityNewTabTransitionTest {
    @get:Rule val coroutineRule = CoroutineTestRule()

    private val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
    private val host = controller.get()
    private val pager = ViewPager2(host)
    private val browser: BrowserActivity = mock(defaultAnswer = CALLS_REAL_METHODS)
    private val adapter: TabPagerAdapter = mock()
    private val viewModel: BrowserViewModel = mock {
        onBlocking { onNewTabRequested(anyOrNull()) } doReturn ADDED
        onBlocking { onOpenInNewTabRequested(any(), anyOrNull(), any()) } doReturn ADDED
    }
    private val tabManager: TabManager = mock {
        onBlocking { openNewTab(anyOrNull(), anyOrNull(), any()) } doReturn ADDED
    }
    private val state = RealNewTabTransitionState().apply { shownTabId = SOURCE }
    private val pending = spy(RealPendingNewTabTransitions(mock(), coroutineRule.testScope, coroutineRule.testDispatcherProvider))
    private val player: NewTabTransitionPlayer = mock()
    private val coordinator = NewTabTransitionCoordinator(player, pending, state) {}
    private var swiping = true
    private var listed = listOf(SOURCE, ADDED, OTHER)
    private lateinit var binding: ActivityBrowserBinding

    init {
        doReturn(host.lifecycle).whenever(browser).lifecycle
        doReturn(emptyWindow()).whenever(browser).window
        browser.swipingTabsFeature = mock { on { isEnabled } doAnswer { swiping } }
        browser.pendingNewTabTransitions = pending
        browser.tabManager = tabManager
        browser.currentBrowserMode = BrowserMode.REGULAR
        setField("tabPager\$delegate", lazyOf(pager))
        setField("tabPagerAdapter\$delegate", lazyOf(adapter))
        setField("newTabTransitionCoordinator\$delegate", lazyOf(coordinator))
        setField("viewModel\$delegate", lazyOf(viewModel))
        setField("binding\$delegate", bindingDelegate())
        setField("pendingInputModeTargets", mutableMapOf<String, InputMode>())
        setField("externalLaunchTabIds", mutableSetOf<String>())
        setField("newTabTransitionConfig", NewTabTransitionConfig.DEFAULT)
        whenever(adapter.itemCount).thenAnswer { listed.size }
        whenever(adapter.getTabIdAtPosition(any())).thenAnswer { listed.getOrNull(it.getArgument(0)) }
        coordinator.onBrowserResumed()
    }

    @After
    fun tearDown() {
        pending.clear()
        if (host.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.pause()
        if (host.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) controller.stop()
        controller.destroy()
    }

    // Toolbar menu New tab, navigation bar new tab button, tabs button long press, chat menu New tab and New Fire tab.

    @Test
    fun newTabGrowsIn() {
        browser.launchNewTab(animated = true)
        assertRegistered()
    }

    @Test
    fun newTabGrowsInWithSwipingOff() {
        swiping = false
        browser.launchNewTab(animated = true)
        assertRegistered()
    }

    @Test
    fun newTabWithSearchInputGrowsIn() {
        browser.launchNewTab(inputModeTarget = InputMode.SEARCH, animated = true)
        assertRegistered()
    }

    @Test
    fun newFireTabInFireModeGrowsIn() {
        browser.currentBrowserMode = BrowserMode.FIRE
        browser.launchNewTab(browserMode = BrowserMode.FIRE, animated = true)
        assertRegistered()
    }

    @Test
    fun newTabThatSwitchesModeDoesNotGrowIn() {
        whenever(viewModel.switchToMode(BrowserMode.FIRE, BrowserModeSwitchSource.NEW_TAB)).thenReturn(true)
        browser.launchNewTab(browserMode = BrowserMode.FIRE, animated = true)
        verifyBlocking(tabManager, never()) { openNewTab(anyOrNull(), anyOrNull(), any()) }
        assertNotRegistered()
    }

    @Test
    fun newTabAfterARejectedModeSwitchDoesNotGrowIn() {
        whenever(viewModel.switchToMode(BrowserMode.FIRE, BrowserModeSwitchSource.NEW_TAB)).thenReturn(false)
        browser.launchNewTab(browserMode = BrowserMode.FIRE, animated = true)
        assertTabOpened()
        assertNotRegistered()
    }

    // Burning from the return hatch, a new search intent and the browser's own open-in-new-tab command.

    @Test
    fun newTabNotAskedToAnimateDoesNotGrowIn() {
        browser.launchNewTab()
        assertTabOpened()
        assertNotRegistered()
    }

    // Open in new tab, from a link's menu or any other in-page action.

    @Test
    fun linkOpenedInNewTabGrowsIn() {
        browser.launchNewTab(URL, SOURCE, animated = true)
        assertRegistered()
    }

    @Test
    fun linkOpenedInNewTabGrowsInWithSwipingOff() {
        swiping = false
        browser.openInNewTab(URL, SOURCE, animated = true)
        assertRegistered()
    }

    @Test
    fun linkOpenedInNewTabNotAskedToAnimateDoesNotGrowIn() {
        swiping = false
        browser.openInNewTab(URL, SOURCE)
        verifyBlocking(viewModel) { onOpenInNewTabRequested(any(), anyOrNull(), any()) }
        assertNotRegistered()
    }

    @Test
    fun linkOpenedInNewFireTabGrowsIn() {
        browser.currentBrowserMode = BrowserMode.FIRE
        browser.launchNewTab(URL, SOURCE, browserMode = BrowserMode.FIRE, animated = true)
        assertRegistered()
    }

    // A page opening a new tab (target="_blank", window.open).

    @Test
    fun tabOpenedByAPageGrowsIn() {
        browser.openMessageInNewTab(Message.obtain(), SOURCE)
        assertRegistered()
    }

    @Test
    fun tabOpenedByAPageGrowsInWithSwipingOff() {
        swiping = false
        doReturn(mock<FragmentManager> { on { isStateSaved } doReturn true }).whenever(browser).supportFragmentManager
        browser.openMessageInNewTab(Message.obtain(), SOURCE)
        assertRegistered()
    }

    @Test
    fun newTabDoesNotGrowInWithTransitionsOff() {
        setField("newTabTransitionConfig", null)
        browser.launchNewTab(animated = true)
        assertTabOpened()
        assertNotRegistered()
    }

    @Test
    fun newTabDoesNotGrowInWhenTheBrowserIsNotInFront() {
        controller.pause()
        browser.launchNewTab(animated = true)
        assertTabOpened()
        assertNotRegistered()
    }

    @Test
    fun stoppingTheBrowserEndsItsTransitions() {
        register(ADDED)
        assertTrue(coordinator.isShowing(SOURCE))

        // The framework's half of onStop can't run on a partial mock and throws; the browser's own cleanup runs before it.
        runCatching { BrowserActivity::class.java.getDeclaredMethod("onStop").apply { isAccessible = true }.invoke(browser) }

        verify(player).cancel()
        assertFalse(coordinator.isShowing(SOURCE))
        assertNull(pending.peek(ADDED))
    }

    @Test
    fun newTabAddedAfterTheBrowserStoppedDoesNotGrowIn() {
        val added = CompletableDeferred<String>()
        tabManager.stub { onBlocking { openNewTab(anyOrNull(), anyOrNull(), any()) } doSuspendableAnswer { added.await() } }
        browser.launchNewTab(animated = true)
        controller.pause().stop()
        added.complete(ADDED)
        assertTabOpened()
        assertNotRegistered()
    }

    @Test
    fun hiddenTabIsCoveredWhenItsPictureArrives() {
        val transition = register(ADDED)
        transition.picture.complete(picture())
        verify(player).cover(any(), any(), any())
    }

    @Test
    fun tabAlreadyOnScreenWhenPictureArrivesIsNotCovered() {
        val transition = register(ADDED)
        listed = listOf(ADDED, SOURCE, OTHER)
        transition.picture.complete(picture())
        verify(player, never()).cover(any(), any(), any())
        assertNull(pending.peek(ADDED))
    }

    @Test
    fun tabShownBeforePictureArrivesIsNotCovered() {
        val transition = register(ADDED)
        coordinator.onTabShowing(ADDED, pager)
        transition.picture.complete(picture())
        verify(player, never()).cover(any(), any(), any())
        assertNull(pending.peek(ADDED))
    }

    @Test
    fun browserStoppedBeforePictureArrivesCoversNothing() {
        val transition = register(ADDED)
        coordinator.cancel()
        coordinator.onBrowserStopped()
        pending.clear()
        transition.picture.complete(picture())
        verify(player, never()).cover(any(), any(), any())
    }

    @Test
    fun olderPictureDoesNotCoverAReplacementRegistration() {
        val first = register(ADDED)
        listed = listOf(SOURCE, ADDED, OTHER, REPLACEMENT)
        val second = register(REPLACEMENT)
        first.picture.complete(picture())
        verify(player, never()).cover(any(), any(), any())
        assertSame(second.pending, pending.peek(REPLACEMENT))
        second.picture.complete(picture())
        verify(player).cover(any(), any(), any())
    }

    @Test
    fun failedCaptureDropsTheTransition() {
        val transition = register(ADDED)
        transition.picture.complete(null)
        verify(player, never()).cover(any(), any(), any())
        assertNull(pending.peek(ADDED))
    }

    @Test
    fun otherTabSelectedWhileAwaitingPictureIsNotCovered() {
        val transition = register(ADDED)
        listed = listOf(OTHER, SOURCE, ADDED)
        coordinator.onTabShowing(OTHER, pager)
        transition.picture.complete(picture())
        verify(player, never()).cover(any(), any(), any())
        assertNull(pending.peek(ADDED))
    }

    // The new tab showing is what plays its transition, so each way the browser shows a tab has to tell the coordinator.

    @Test
    fun selectingTheNewTabPlaysItsTransition() {
        swiping = false
        setField("lastActiveTabs", BrowserActivity::class.java.getDeclaredField("lastActiveTabs").type.getDeclaredConstructor().newInstance())
        val fragments: FragmentManager = mock {
            on { findFragmentByTag(ADDED) } doReturn mock<BrowserTabFragment>()
            on { beginTransaction() } doReturn mock()
        }
        doReturn(fragments).whenever(browser).supportFragmentManager
        pending.register(ADDED, readyTransition())

        invoke("selectTab", TabEntity::class.java to TabEntity(tabId = ADDED))

        verify(player).play(eq(binding.fragmentContainer), any(), any(), any())
    }

    @Test
    fun movingThePagerToTheNewTabPlaysItsTransition() {
        host.setContentView(pager)
        pending.register(ADDED, readyTransition())

        invoke("onMoveToTabRequested", Int::class.javaPrimitiveType!! to listed.indexOf(ADDED))
        shadowOf(Looper.getMainLooper()).idle()

        verify(player).play(eq(pager), any(), any(), any())
    }

    @Test
    fun newTabListedInThePagerIsCovered() {
        pending.register(ADDED, readyTransition())

        invoke(
            "onTabsUpdated",
            List::class.java to listOf(TabModel(SOURCE, null, false, null), TabModel(ADDED, null, false, SOURCE)),
            Bundle::class.java to null,
            Boolean::class.javaPrimitiveType!! to false,
        )

        verify(player).cover(eq(pager), any(), any())
    }

    @Test
    fun tabOpenedByAPageWithSwipingOffPlaysItsTransition() {
        swiping = false
        doReturn(mock<FragmentManager> { on { isStateSaved } doReturn true }).whenever(browser).supportFragmentManager
        // The browser's own picture comes back empty here, so a ready one stands in for it.
        doReturn(readyTransition()).whenever(pending).consume(ADDED)

        browser.openMessageInNewTab(Message.obtain(), SOURCE)

        verify(player).play(eq(binding.fragmentContainer), any(), any(), any())
    }

    private fun assertRegistered() {
        verify(pending).register(eq(ADDED), any())
    }

    private fun assertTabOpened() {
        verifyBlocking(tabManager) { openNewTab(anyOrNull(), anyOrNull(), any()) }
    }

    private fun assertNotRegistered() {
        verify(pending, never()).register(any(), any())
    }

    private fun register(tabId: String): Registration {
        val picture = CompletableDeferred<Bitmap?>()
        val transition = PendingNewTabTransition(picture, NewTabTransitionConfig.DEFAULT.motionFor(NewTabOrigin.RIGHT_OF_CENTER))
        BrowserActivity::class.java.getDeclaredMethod("registerNewTabTransition", String::class.java, PendingNewTabTransition::class.java).apply {
            isAccessible = true
        }.invoke(browser, tabId, transition)
        return Registration(picture, transition)
    }

    // Only the non-swiping registration reads the binding, for its fragment container. Unit tests here have no layouts to
    // inflate, so the binding is built around that one view.
    private fun bindingDelegate(): Any {
        val constructor = ActivityBrowserBinding::class.java.declaredConstructors.single().apply { isAccessible = true }
        val views = constructor.parameterTypes.map { if (it == FrameLayout::class.java) FrameLayout(host) else null }
        binding = constructor.newInstance(*views.toTypedArray()) as ActivityBrowserBinding
        val delegate = BrowserActivity::class.java.getDeclaredField("binding\$delegate").type
            .getConstructor(Class::class.java, AppCompatActivity::class.java)
            .newInstance(ActivityBrowserBinding::class.java, mock<AppCompatActivity>())
        delegate.javaClass.getDeclaredField("binding").apply { isAccessible = true }.set(delegate, binding)
        return delegate
    }

    private fun emptyWindow(): Window = mock { on { decorView } doReturn View(host) }

    private fun picture() = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888)

    private fun readyTransition() =
        PendingNewTabTransition(CompletableDeferred(picture()), NewTabTransitionConfig.DEFAULT.motionFor(NewTabOrigin.RIGHT_OF_CENTER))

    private fun invoke(name: String, vararg args: Pair<Class<*>, Any?>) {
        BrowserActivity::class.java.getDeclaredMethod(name, *args.map { it.first }.toTypedArray()).apply { isAccessible = true }
            .invoke(browser, *args.map { it.second }.toTypedArray())
    }

    private fun setField(name: String, value: Any?) {
        BrowserActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(browser, value)
    }

    private data class Registration(val picture: CompletableDeferred<Bitmap?>, val pending: PendingNewTabTransition)

    private companion object {
        const val SOURCE = "source"
        const val ADDED = "added"
        const val OTHER = "other"
        const val REPLACEMENT = "replacement"
        const val URL = "https://example.com"
    }
}
