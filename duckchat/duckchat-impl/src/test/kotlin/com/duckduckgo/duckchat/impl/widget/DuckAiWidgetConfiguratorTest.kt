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

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.tabs.DuckChatLaunch
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class DuckAiWidgetConfiguratorTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val duckChat: DuckChatInternal = mock()
    private val chatStore: DuckAiChatStore = mock()
    private val widgetPreferences: DuckAiWidgetPreferences = mock<DuckAiWidgetPreferences> {
        onBlocking { showRecentChats(any()) } doReturn true
    }
    private val remoteViews: RemoteViews = mock()
    private val stringContext: Context = mock<Context>().also { whenever(it.getString(any())).thenReturn("Untitled chat") }
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private val testee = DuckAiWidgetConfigurator(
        duckChat = duckChat,
        chats = DuckAiChatsWidgetChats(stringContext, chatStore),
        widgetPreferences = widgetPreferences,
        intents = fakeDuckAiWidgetIntents(),
        dispatcherProvider = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenPromptBarTappedThenLaunchesNewChatFromWidget() = runTest {
        stubDuckAi(enabled = true, historyAvailable = false)

        val launched = populateAndCaptureClickIntent(R.id.promptBarContainer)

        assertLaunch(launched, url = NEW_CHAT_URL)
    }

    @Test
    fun whenVoiceActionTappedThenLaunchesVoiceMode() = runTest {
        stubDuckAi(enabled = true, historyAvailable = false)

        val launched = populateAndCaptureClickIntent(R.id.actionVoice)

        assertLaunch(launched, url = VOICE_URL)
    }

    @Test
    fun whenCreateImageTappedThenLaunchesImageGeneration() = runTest {
        stubDuckAi(enabled = true, historyAvailable = false)

        val launched = populateAndCaptureClickIntent(R.id.actionCreateImage)

        assertLaunch(launched, url = NEW_CHAT_URL, launch = DuckChatLaunch.ImageGeneration::class)
    }

    @Test
    fun whenTakePhotoTappedThenLaunchesCameraCapture() = runTest {
        stubDuckAi(enabled = true, historyAvailable = false)

        val launched = populateAndCaptureClickIntent(R.id.actionTakePhoto)

        assertLaunch(launched, url = NEW_CHAT_URL, launch = DuckChatLaunch.CameraCapture::class)
    }

    @Test
    fun whenChatHistoryAvailableThenViewAllShownAndOpensChatHistory() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)

        val launched = populateAndCaptureClickIntent(R.id.chatsViewAll)

        assertLaunch(launched, launch = DuckChatLaunch.ChatHistory::class)
        verify(remoteViews).setViewVisibility(R.id.chatsViewAllContainer, View.VISIBLE)
    }

    @Test
    fun whenShowRecentChatsOnThenChatsSectionVisible() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        verify(remoteViews).setViewVisibility(R.id.chatsSection, View.VISIBLE)
    }

    @Test
    fun whenShowRecentChatsOffThenChatsSectionHiddenAndChatsNotLoadedOrWired() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)
        whenever(widgetPreferences.showRecentChats(APP_WIDGET_ID)).thenReturn(false)

        val launched = populateAndCaptureClickIntent(R.id.promptBarContainer)

        assertLaunch(launched, url = NEW_CHAT_URL)
        verify(remoteViews).setViewVisibility(R.id.chatsSection, View.GONE)
        verify(remoteViews, never()).setOnClickPendingIntent(eq(R.id.chatsViewAll), any())
        verify(remoteViews, never()).setRemoteAdapter(eq(R.id.chatsList), any<RemoteViews.RemoteCollectionItems>())
        verify(chatStore, never()).getChats()
    }

    @Test
    fun whenChatHistoryUnavailableThenViewAllHiddenNotWiredAndUnavailableMessageShown() = runTest {
        stubDuckAi(enabled = true, historyAvailable = false)

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        verify(remoteViews).setViewVisibility(R.id.chatsViewAllContainer, View.GONE)
        verify(remoteViews, never()).setOnClickPendingIntent(eq(R.id.chatsViewAll), any())
        verify(remoteViews).setViewVisibility(R.id.chatsList, View.GONE)
        verify(remoteViews).setViewVisibility(R.id.chatsMessageUnavailable, View.VISIBLE)
        verify(remoteViews).setViewVisibility(R.id.chatsMessageEmpty, View.GONE)
        verify(chatStore, never()).getChats()
    }

    @Test
    @Config(sdk = [34])
    fun whenChatsAvailableThenListShownAndTemplateLaunchesDuckAi() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)
        whenever(chatStore.getChats()).thenReturn(listOf(chat("a"), chat("b", pinned = true)))
        whenever(duckChat.buildChatUrl(any())).thenAnswer { "https://duck.ai/?chatID=${it.arguments[0]}" }

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        verify(remoteViews).setViewVisibility(R.id.chatsList, View.VISIBLE)
        verify(remoteViews).setViewVisibility(R.id.chatsMessageEmpty, View.GONE)
        verify(remoteViews).setViewVisibility(R.id.chatsMessageUnavailable, View.GONE)
        val captor = argumentCaptor<PendingIntent>()
        verify(remoteViews).setPendingIntentTemplate(eq(R.id.chatsList), captor.capture())
        assertEquals(null, shadowOf(captor.firstValue).savedIntent.extras)
        assertEquals(DuckAiWidgetTarget.CHAT.requestCode, shadowOf(captor.firstValue).requestCode)
        verify(duckChat).buildChatUrl("a")
        verify(duckChat).buildChatUrl("b")
    }

    @Test
    @Config(sdk = [34])
    fun whenChatsAvailableThenRowsHaveNoSectionHeadersAndPinnedChatsComeFirstWithPinnedIcon() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)
        whenever(chatStore.getChats()).thenReturn(
            listOf(
                chat("recent-old", lastEdit = "2026-01-01T00:00:00.000Z"),
                chat("recent-new", lastEdit = "2026-03-01T00:00:00.000Z"),
                chat("pinned", pinned = true, lastEdit = "2026-02-01T00:00:00.000Z"),
            ),
        )
        whenever(duckChat.buildChatUrl(any())).thenAnswer { "https://duck.ai/?chatID=${it.arguments[0]}" }

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        val captor = argumentCaptor<RemoteViews.RemoteCollectionItems>()
        verify(remoteViews).setRemoteAdapter(eq(R.id.chatsList), captor.capture())
        val items = captor.firstValue
        val rows = (0 until items.itemCount).map { items.getItemView(it).apply(activity, FrameLayout(activity)) }
        assertEquals(3, rows.size)
        assertEquals(
            listOf("Title pinned", "Title recent-new", "Title recent-old"),
            rows.map {
                it.findViewById<TextView>(R.id.chatsItemTitle).text.toString()
            },
        )
        assertEquals(
            listOf(R.drawable.ic_pin_24_daynight, R.drawable.ic_chat_24_daynight, R.drawable.ic_chat_24_daynight),
            rows.map { shadowOf(it.findViewById<ImageView>(R.id.chatsItemIcon).drawable).createdFromResId },
        )
    }

    @Test
    @Config(sdk = [34])
    fun whenChatsOfEachTypeThenRowIconFollowsNativeTypeAndPinnedRules() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)
        val chats = listOf(
            chat("discussion", lastEdit = "2026-01-01T00:00:01.000Z"),
            chat("image", isImageGeneration = true, lastEdit = "2026-01-01T00:00:02.000Z"),
            chat("voice", isVoice = true, lastEdit = "2026-01-01T00:00:03.000Z"),
            chat("both", isImageGeneration = true, isVoice = true, lastEdit = "2026-01-01T00:00:04.000Z"),
            chat("pinned-discussion", pinned = true, lastEdit = "2026-01-01T00:00:05.000Z"),
            chat("pinned-image", pinned = true, isImageGeneration = true, lastEdit = "2026-01-01T00:00:06.000Z"),
            chat("pinned-voice", pinned = true, isVoice = true, lastEdit = "2026-01-01T00:00:07.000Z"),
        )
        whenever(chatStore.getChats()).thenReturn(chats)
        whenever(duckChat.buildChatUrl(any())).thenAnswer { "https://duck.ai/?chatID=${it.arguments[0]}" }

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        val captor = argumentCaptor<RemoteViews.RemoteCollectionItems>()
        verify(remoteViews).setRemoteAdapter(eq(R.id.chatsList), captor.capture())
        val items = captor.firstValue
        val iconsByTitle = (0 until items.itemCount).map { items.getItemView(it).apply(activity, FrameLayout(activity)) }.associate {
            it.findViewById<TextView>(R.id.chatsItemTitle).text.toString() to
                shadowOf(it.findViewById<ImageView>(R.id.chatsItemIcon).drawable).createdFromResId
        }
        assertEquals(R.drawable.ic_chat_24_daynight, iconsByTitle["Title discussion"])
        assertEquals(R.drawable.ic_images_24_daynight, iconsByTitle["Title image"])
        assertEquals(R.drawable.ic_voice_24_daynight, iconsByTitle["Title voice"])
        assertEquals(R.drawable.ic_images_24_daynight, iconsByTitle["Title both"])
        assertEquals(R.drawable.ic_pin_24_daynight, iconsByTitle["Title pinned-discussion"])
        assertEquals(R.drawable.ic_images_pin_24_daynight, iconsByTitle["Title pinned-image"])
        assertEquals(R.drawable.ic_voice_pin_24_daynight, iconsByTitle["Title pinned-voice"])
    }

    @Test
    fun whenChatTappedThenFillInIntentOpensThatChat() {
        val fillIn = fakeDuckAiWidgetIntents().chatFillInIntent(activity, CHAT_URL)

        assertLaunch(fillIn, url = CHAT_URL)
    }

    @Test
    fun whenNoChatsThenEmptyMessageShownAndActionsStillWired() = runTest {
        stubDuckAi(enabled = true, historyAvailable = true)
        whenever(chatStore.getChats()).thenReturn(emptyList())

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        verify(remoteViews).setViewVisibility(R.id.chatsList, View.GONE)
        verify(remoteViews).setViewVisibility(R.id.chatsMessageEmpty, View.VISIBLE)
        verify(remoteViews).setViewVisibility(R.id.chatsMessageUnavailable, View.GONE)
        verify(remoteViews).setOnClickPendingIntent(eq(R.id.chatsViewAll), any())
        verify(remoteViews, never()).setPendingIntentTemplate(any(), any())
    }

    @Test
    fun whenDuckAiDisabledThenNoClickTargetsWiredAndUnavailableMessageShown() = runTest {
        whenever(duckChat.isEnabled()).thenReturn(false)

        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)

        verify(remoteViews, never()).setOnClickPendingIntent(any(), any())
        verify(remoteViews, never()).setPendingIntentTemplate(any(), any())
        verify(remoteViews).setViewVisibility(R.id.chatsMessageUnavailable, View.VISIBLE)
        verify(remoteViews).setViewVisibility(R.id.chatsMessageEmpty, View.GONE)
        verify(chatStore, never()).getChats()
    }

    @Test
    fun whenAllTargetsBuiltThenRequestCodesAreDistinct() {
        val requestCodes = DuckAiWidgetTarget.entries.map { it.requestCode }

        assertEquals(requestCodes.size, requestCodes.toSet().size)
    }

    private suspend fun stubDuckAi(
        enabled: Boolean,
        historyAvailable: Boolean,
    ) {
        whenever(duckChat.isEnabled()).thenReturn(enabled)
        whenever(duckChat.isChatHistoryAvailable()).thenReturn(historyAvailable)
        whenever(duckChat.getDuckChatUrl("", false)).thenReturn(NEW_CHAT_URL)
        whenever(duckChat.getVoiceDuckChatUrl()).thenReturn(VOICE_URL)
        if (historyAvailable) whenever(chatStore.getChats()).thenReturn(emptyList())
    }

    private suspend fun populateAndCaptureClickIntent(viewId: Int): Intent {
        testee.populateRemoteViews(activity, APP_WIDGET_ID, remoteViews)
        val captor = argumentCaptor<PendingIntent>()
        verify(remoteViews).setOnClickPendingIntent(eq(viewId), captor.capture())
        return shadowOf(captor.firstValue).savedIntent
    }

    private fun chat(
        id: String,
        pinned: Boolean = false,
        lastEdit: String = "2026-01-01T00:00:00.000Z",
        isImageGeneration: Boolean = false,
        isVoice: Boolean = false,
    ) = DuckAiChat(
        chatId = id,
        title = "Title $id",
        model = "model",
        lastEdit = lastEdit,
        pinned = pinned,
        isImageGeneration = isImageGeneration,
        isVoice = isVoice,
    )

    private companion object {
        const val APP_WIDGET_ID = 7
        const val NEW_CHAT_URL = "https://duck.ai/"
        const val VOICE_URL = "https://duck.ai/?mode=voice-mode"
        const val CHAT_URL = "https://duck.ai/?chatID=abc"
    }
}
