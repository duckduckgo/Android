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

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class DuckAiWidgetUpdaterTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val stringContext: Context = mock<Context>().also { whenever(it.getString(any())).thenReturn("Untitled chat") }
    private val chatsFlow = MutableStateFlow(listOf(chat("a")))
    private val chatStore: DuckAiChatStore = mock<DuckAiChatStore>().also { whenever(it.getChatsFlow()).thenReturn(chatsFlow) }

    private val testee = DuckAiWidgetUpdater(
        context = context,
        chats = DuckAiChatsWidgetChats(stringContext, chatStore),
        appCoroutineScope = coroutineTestRule.testScope,
    )

    @Test
    fun whenNoWidgetInstanceThenChatChangesDoNotBroadcastAnUpdate() {
        testee.startObservingIfNeeded()

        chatsFlow.value = listOf(chat("a"), chat("b"))

        assertTrue(shadowOf(context).broadcastIntents.isEmpty())
    }

    @Test
    fun whenWidgetInstanceExistsThenInitialEmissionIsSkippedAndChangeBroadcastsUpdateForItsIds() {
        val widgetId = createWidget()
        testee.startObservingIfNeeded()
        assertTrue(shadowOf(context).broadcastIntents.isEmpty())

        chatsFlow.value = listOf(chat("a"), chat("b"))

        val intent = shadowOf(context).broadcastIntents.single()
        assertEquals(DuckAiWidgetProvider::class.java.name, intent.component?.className)
        assertEquals(AppWidgetManager.ACTION_APPWIDGET_UPDATE, intent.action)
        assertEquals(listOf(widgetId), intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)?.toList())
    }

    @Test
    fun whenChatsChangeInWaysNotDisplayedThenNoBroadcast() {
        createWidget()
        testee.startObservingIfNeeded()

        chatsFlow.value = listOf(chat("a").copy(model = "other-model", lastEdit = "2026-09-09T00:00:00.000Z"))

        assertTrue(shadowOf(context).broadcastIntents.isEmpty())
    }

    @Test
    fun whenTitleOrPinnedChangesThenBroadcasts() {
        createWidget()
        testee.startObservingIfNeeded()

        chatsFlow.value = listOf(chat("a").copy(pinned = true))
        chatsFlow.value = listOf(chat("a").copy(pinned = true, title = "Renamed"))

        assertEquals(2, shadowOf(context).broadcastIntents.size)
    }

    @Test
    fun whenStartedTwiceThenOnlyOneCollectorBroadcasts() {
        createWidget()
        testee.startObservingIfNeeded()
        testee.startObservingIfNeeded()

        chatsFlow.value = listOf(chat("a"), chat("b"))

        assertEquals(1, shadowOf(context).broadcastIntents.size)
    }

    @Test
    fun whenPrivacyConfigDownloadedThenPlacedWidgetIsRefreshed() {
        val widgetId = createWidget()

        testee.onPrivacyConfigDownloaded()

        val intent = shadowOf(context).broadcastIntents.single()
        assertEquals(DuckAiWidgetProvider::class.java.name, intent.component?.className)
        assertEquals(listOf(widgetId), intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)?.toList())
    }

    @Test
    fun whenPrivacyConfigDownloadedWithoutWidgetsThenNoBroadcast() {
        testee.onPrivacyConfigDownloaded()

        assertTrue(shadowOf(context).broadcastIntents.isEmpty())
    }

    private fun createWidget(widgetId: Int = WIDGET_ID): Int {
        val provider = ComponentName(context, DuckAiWidgetProvider::class.java)
        val manager = AppWidgetManager.getInstance(context)
        shadowOf(manager).addInstalledProvider(AppWidgetProviderInfo().apply { this.provider = provider })
        manager.bindAppWidgetIdIfAllowed(widgetId, provider)
        return widgetId
    }

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
