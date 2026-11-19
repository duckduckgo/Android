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

package com.duckduckgo.duckchat.impl.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.widget.api.HomeScreenWidgetLifecycle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class DuckAiWidgetProviderTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager: AppWidgetManager = mock()
    private val chatStore: DuckAiChatStore = mock()
    private val duckChat: DuckChatInternal = mock()
    private val repository: DuckChatFeatureRepository = mock()
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val stringContext: Context = mock<Context>().also { whenever(it.getString(any())).thenReturn("Untitled chat") }
    private val pixel: Pixel = mock()
    private val homeScreenWidgetLifecycle: HomeScreenWidgetLifecycle = mock()
    private val testee = DuckAiWidgetProvider().also {
        it.pixel = pixel
        it.homeScreenWidgetLifecycle = homeScreenWidgetLifecycle
        it.duckAiWidgetUpdater = mock()
        it.appCoroutineScope = coroutineTestRule.testScope
        it.duckAiWidgetConfigurator = DuckAiWidgetConfigurator(
            duckChatFeature = duckChatFeature,
            repository = repository,
            duckChat = duckChat,
            chats = DuckAiChatsWidgetChats(stringContext, chatStore),
            intents = fakeDuckAiWidgetIntents(),
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenFirstWidgetPlacedThenFiresAddedPixelAndReportsEnabled() {
        testee.onEnabled(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)

        verify(pixel).fire(DuckChatPixelName.DUCK_AI_WIDGET_ADDED)
        verify(homeScreenWidgetLifecycle).onWidgetEnabled()
    }

    @Test
    fun whenLastWidgetRemovedThenFiresDeletedPixelAndReportsDisabled() {
        testee.onDisabled(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext)

        verify(pixel).fire(DuckChatPixelName.DUCK_AI_WIDGET_DELETED)
        verify(homeScreenWidgetLifecycle).onWidgetDisabled()
    }

    @Test
    @Config(sdk = [30], qualifiers = "port")
    fun whenPortraitThenHeightIsMaxHeightAndWidthIsMinWidth() {
        stubChats(listOf(chat("a")))
        stubOptions(minWidth = 360, maxHeight = 400, maxWidth = 500, minHeight = 180)

        testee.onUpdate(context, manager, intArrayOf(WIDGET_ID))

        val root = lastPublished().apply(context, FrameLayout(context))
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.chatsSection).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.labelVoice).visibility)
    }

    @Test
    @Config(sdk = [30], qualifiers = "land")
    fun whenLandscapeThenHeightIsMinHeightAndWidthIsMaxWidth() {
        stubChats(listOf(chat("a")))
        stubOptions(minWidth = 360, maxHeight = 400, maxWidth = 500, minHeight = 180)

        testee.onUpdate(context, manager, intArrayOf(WIDGET_ID))

        val root = lastPublished().apply(context, FrameLayout(context))
        assertEquals(View.GONE, root.findViewById<View>(R.id.chatsSection).visibility)
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.labelVoice).visibility)
    }

    @Test
    @Config(sdk = [30], qualifiers = "port")
    fun whenLauncherReportsNoSizeOptionsThenWidgetIsCompact() {
        stubChats(listOf(chat("a")))
        whenever(manager.getAppWidgetOptions(WIDGET_ID)).thenReturn(Bundle())

        testee.onUpdate(context, manager, intArrayOf(WIDGET_ID))

        val root = lastPublished().apply(context, FrameLayout(context))
        assertEquals(View.GONE, root.findViewById<View>(R.id.chatsSection).visibility)
    }

    private fun lastPublished(): RemoteViews {
        val published = argumentCaptor<RemoteViews>()
        verify(manager, atLeastOnce()).updateAppWidget(eq(WIDGET_ID), published.capture())
        return published.lastValue
    }

    private fun stubOptions(
        minWidth: Int,
        maxHeight: Int,
        maxWidth: Int,
        minHeight: Int,
    ) {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minWidth)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, maxHeight)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, maxWidth)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minHeight)
        }
        whenever(manager.getAppWidgetOptions(WIDGET_ID)).thenReturn(options)
    }

    private fun stubChats(chats: List<DuckAiChat>) {
        runBlockingStub { whenever(chatStore.getChats()).thenReturn(chats) }
        stubDuckAi()
    }

    private fun stubDuckAi() {
        duckChatFeature.self().setRawStoredState(State(true))
        runBlockingStub {
            whenever(repository.isDuckChatUserEnabled()).thenReturn(true)
            whenever(duckChat.isChatHistoryAvailable()).thenReturn(true)
        }
        whenever(duckChat.buildChatUrl(any())).thenAnswer { "https://duck.ai/?chatID=${it.arguments[0]}" }
    }

    private fun runBlockingStub(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }

    private fun chat(id: String) = DuckAiChat(
        chatId = id,
        title = "Title $id",
        model = "model",
        lastEdit = "2026-01-01T00:00:00.000Z",
        pinned = false,
    )

    private companion object {
        const val WIDGET_ID = 42
    }
}
