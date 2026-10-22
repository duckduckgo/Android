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

package com.duckduckgo.duckchat.impl.tabswitcher

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemCallbacks
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemKey
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemState
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemState.Selection
import com.duckduckgo.duckchat.impl.R
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class DuckAiTabGridCardViewTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val contentSource = FakeContentSource()
    private val card = DuckAiTabGridCardView(
        context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext,
            com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light,
        ),
        contentSource = contentSource,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        coroutineScope = coroutineTestRule.testScope,
    )

    @Test
    fun whenContentAlreadyKnownThenBoundWithoutLoading() {
        contentSource.known[KEY] = RealDuckAiTabGridItemContent.Chat("Chat title", "Snippet")

        card.bind(KEY, state(), callbacks)

        assertEquals(View.VISIBLE, view<View>(R.id.snippet).visibility)
        assertEquals("Snippet", view<TextView>(R.id.snippet).text.toString())
        assertEquals(0, contentSource.loads)
    }

    @Test
    fun whenContentUnknownThenPlaceholderShowsHostTitleUntilLoaded() {
        val result = contentSource.pending(KEY)

        card.bind(KEY, state(title = "Host title"), callbacks)

        assertEquals("Host title", view<TextView>(R.id.tabChatItemTitle).text.toString())
        assertEquals(View.GONE, view<View>(R.id.snippet).visibility)
        assertEquals(View.GONE, view<View>(R.id.tabChatItemType).visibility)

        result.complete(RealDuckAiTabGridItemContent.Chat("Chat title", "Snippet"))

        assertEquals("Chat title", view<TextView>(R.id.tabChatItemTitle).text.toString())
        assertEquals(View.VISIBLE, view<View>(R.id.snippet).visibility)
        assertEquals(View.VISIBLE, view<View>(R.id.tabChatItemType).visibility)
    }

    @Test
    fun whenReboundToAnotherTabBeforeLoadCompletesThenStaleResultIgnored() {
        val stale = contentSource.pending(KEY)
        contentSource.known[OTHER_KEY] = RealDuckAiTabGridItemContent.Empty

        card.bind(KEY, state(), callbacks)
        card.bind(OTHER_KEY, state(), callbacks)
        stale.complete(RealDuckAiTabGridItemContent.Chat("Chat title", "Snippet"))

        assertEquals(View.VISIBLE, view<View>(R.id.tabChatItemEmptyLogo).visibility)
        assertEquals(View.GONE, view<View>(R.id.tabChatItemDetails).visibility)
    }

    @Test
    fun whenSameTabReboundThenNotLoadedAgain() {
        contentSource.pending(KEY)

        card.bind(KEY, state(), callbacks)
        card.bind(KEY, state(selection = Selection.Selectable(isSelected = true)), callbacks)

        assertEquals(1, contentSource.loads)
    }

    @Test
    fun whenImageChatWithoutThumbnailThenLogoShownInItsPlace() {
        contentSource.known[KEY] = RealDuckAiTabGridItemContent.ImageChat("Image title", "file-1", thumbnail = null)

        card.bind(KEY, state(), callbacks)

        assertEquals("Image title", view<TextView>(R.id.tabChatItemTitle).text.toString())
        assertEquals(View.VISIBLE, view<View>(R.id.thumbnail).visibility)
        assertEquals(ImageView.ScaleType.CENTER_INSIDE, view<ImageView>(R.id.thumbnail).scaleType)
        assertEquals(0, contentSource.loads)
    }

    @Test
    fun whenFallbackWithoutScreenshotThenEmptyLogoShown() {
        contentSource.known[KEY] = RealDuckAiTabGridItemContent.Fallback

        card.bind(KEY, state(screenshotPath = null), callbacks)

        assertEquals(View.VISIBLE, view<View>(R.id.tabChatItemEmptyLogo).visibility)
        assertEquals(View.GONE, view<View>(R.id.tabChatItemScreenshot).visibility)
    }

    @Test
    fun whenFallbackWithScreenshotThenScreenshotShown() {
        contentSource.known[KEY] = RealDuckAiTabGridItemContent.Fallback

        card.bind(KEY, state(screenshotPath = "/previews/tab.jpg"), callbacks)

        assertEquals(View.VISIBLE, view<View>(R.id.tabChatItemScreenshot).visibility)
        assertEquals(View.GONE, view<View>(R.id.tabChatItemEmptyLogo).visibility)
    }

    @Test
    fun whenScreenshotArrivesLaterThenShownWithoutReloadingContent() {
        contentSource.known[KEY] = RealDuckAiTabGridItemContent.Fallback
        card.bind(KEY, state(screenshotPath = null), callbacks)

        card.bind(KEY, state(screenshotPath = "/previews/tab.jpg"), callbacks)

        assertEquals(View.VISIBLE, view<View>(R.id.tabChatItemScreenshot).visibility)
        assertEquals(0, contentSource.loads)
    }

    @Test
    fun whenThinkingCardReboundThenReloadedAndShowsStoredReply() {
        contentSource.pending(KEY).complete(RealDuckAiTabGridItemContent.Thinking("Chat title"))
        card.bind(KEY, state(), callbacks)
        val reply = contentSource.pending(KEY)

        card.bind(KEY, state(), callbacks)
        reply.complete(RealDuckAiTabGridItemContent.Chat("Chat title", "Reply"))

        assertEquals(2, contentSource.loads)
        assertEquals("Reply", view<TextView>(R.id.snippet).text.toString())
    }

    @Test
    fun whenThinkingCardReboundWhileReloadingThenNotLoadedAgain() {
        contentSource.pending(KEY).complete(RealDuckAiTabGridItemContent.Thinking("Chat title"))
        card.bind(KEY, state(), callbacks)
        contentSource.pending(KEY)

        card.bind(KEY, state(), callbacks)
        card.bind(KEY, state(), callbacks)

        assertEquals(2, contentSource.loads)
    }

    @Test
    fun whenDetachedWhileLoadingThenContentShownOnceLoaded() {
        val result = contentSource.pending(KEY)
        card.bind(KEY, state(), callbacks)
        val activity = attachToWindow()
        activity.setContentView(View(activity))

        result.complete(RealDuckAiTabGridItemContent.Chat("Chat title", "Snippet"))

        assertEquals("Snippet", view<TextView>(R.id.snippet).text.toString())
        assertEquals(1, contentSource.loads)
    }

    private fun attachToWindow(activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()): Activity {
        (card.view.parent as? ViewGroup)?.removeView(card.view)
        activity.setContentView(card.view)
        return activity
    }

    private fun <T : View> view(id: Int): T = card.view.findViewById(id)

    private fun state(
        title: String = "Tab title",
        screenshotPath: String? = null,
        selection: Selection = Selection.None,
    ) = DuckAiTabGridItemState(isUnread = false, selection = selection, title = title, screenshotPath = screenshotPath)

    private val callbacks = object : DuckAiTabGridItemCallbacks {
        override fun onClick() = Unit
        override fun onClose() = Unit
    }

    private class FakeContentSource : DuckAiTabGridContentSource {
        val known = mutableMapOf<DuckAiTabGridItemKey, RealDuckAiTabGridItemContent>()
        private val results = mutableMapOf<DuckAiTabGridItemKey, CompletableDeferred<RealDuckAiTabGridItemContent>>()
        var loads = 0

        fun pending(key: DuckAiTabGridItemKey) = CompletableDeferred<RealDuckAiTabGridItemContent>().also { results[key] = it }

        override fun peek(key: DuckAiTabGridItemKey) = known[key]

        override suspend fun load(key: DuckAiTabGridItemKey): RealDuckAiTabGridItemContent {
            loads++
            return results.getValue(key).await()
        }
    }

    private companion object {
        val KEY = DuckAiTabGridItemKey(tabId = "tab", url = "https://duck.ai/?chatID=chat-1")
        val OTHER_KEY = DuckAiTabGridItemKey(tabId = "other", url = "https://duck.ai/?chatID=chat-2")
    }
}
