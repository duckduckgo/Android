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
import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DuckAiWidgetMainThreadTest {

    private val ioDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(ioDispatcher)

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val manager: AppWidgetManager = mock<AppWidgetManager>().also {
        whenever(it.getAppWidgetIds(any())).thenReturn(intArrayOf(WIDGET_ID))
    }
    private val context = object : ContextWrapper(application) {
        override fun getSystemService(name: String): Any? = if (name == APPWIDGET_SERVICE) manager else super.getSystemService(name)
    }

    private val chatsFlow = MutableStateFlow(listOf(chat("a")))
    private val chatStore: DuckAiChatStore = mock<DuckAiChatStore>().also { whenever(it.getChatsFlow()).thenReturn(chatsFlow) }
    private val stringContext: Context = mock<Context>().also { whenever(it.getString(any())).thenReturn("Untitled chat") }

    private val updater = DuckAiWidgetUpdater(
        context = context,
        chats = DuckAiChatsWidgetChats(stringContext, chatStore),
        appCoroutineScope = testScope,
    )

    @Test
    fun whenAppCreatedThenAppWidgetManagerIsQueriedOnlyOnTheIoDispatcher() {
        updater.onCreate(mock())

        assertQueriedOnlyWhenIoDispatcherRuns()
    }

    @Test
    fun whenAppStartedThenAppWidgetManagerIsQueriedOnlyOnTheIoDispatcher() {
        updater.onStart(mock())

        assertQueriedOnlyWhenIoDispatcherRuns()
    }

    @Test
    fun whenWidgetProviderUpdatesThenAppWidgetManagerIsQueriedOnlyOnTheIoDispatcher() {
        val provider = DuckAiWidgetProvider().also {
            it.duckAiWidgetUpdater = updater
            it.duckAiWidgetConfigurator = mock()
            it.appCoroutineScope = CoroutineScope(EmptyCoroutineContext + Job().apply { cancel() })
        }

        provider.onUpdate(context, manager, intArrayOf(WIDGET_ID))

        assertQueriedOnlyWhenIoDispatcherRuns()
    }

    private fun assertQueriedOnlyWhenIoDispatcherRuns() {
        verify(manager, never()).getAppWidgetIds(any())
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
