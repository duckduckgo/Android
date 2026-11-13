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

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import androidx.core.widget.RemoteViewsCompat
import com.duckduckgo.app.tabs.DuckChatLaunch
import com.duckduckgo.app.tabs.DuckChatLaunchOrigin
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.widgetIconRes
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

/** Request codes of the widget's tap targets; each target needs its own so their [android.app.PendingIntent]s stay distinct. */
enum class DuckAiWidgetTarget(val requestCode: Int) {
    PROMPT_BAR(1591),
    VOICE(1592),
    CREATE_IMAGE(1593),
    TAKE_PHOTO(1594),
    VIEW_ALL_CHATS(1597),
    CHAT(1598),
}

class DuckAiWidgetConfigurator @Inject constructor(
    private val duckChat: DuckChatInternal,
    private val chats: DuckAiChatsWidgetChats,
    private val widgetPreferences: DuckAiWidgetPreferences,
    private val intents: DuckAiWidgetIntents,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val widgetLaunch = DuckChatLaunch.Open(DuckChatLaunchOrigin.HOME_SCREEN_WIDGET)

    suspend fun populateRemoteViews(
        context: Context,
        appWidgetId: Int,
        remoteViews: RemoteViews,
    ) {
        val loaded = load(appWidgetId)
        render(context, appWidgetId, remoteViews, loaded, showChats = loaded.showChats)
    }

    private suspend fun load(appWidgetId: Int): Loaded {
        val showChats = widgetPreferences.showRecentChats(appWidgetId)
        val state = withContext(dispatcherProvider.io()) {
            if (!duckChat.isEnabled()) return@withContext null
            loadState(showChats)
        }
        return Loaded(showChats, state)
    }

    private suspend fun render(
        context: Context,
        appWidgetId: Int,
        remoteViews: RemoteViews,
        loaded: Loaded,
        showChats: Boolean,
    ) {
        val state = loaded.state
        withContext(dispatcherProvider.main()) {
            remoteViews.setViewVisibility(R.id.chatsSection, if (showChats) View.VISIBLE else View.GONE)
            if (state == null) {
                logcat { "DuckAiWidgetProvider Duck.ai disabled, click targets not wired" }
                remoteViews.setViewVisibility(R.id.chatsViewAllContainer, View.GONE)
                showMessage(remoteViews, R.id.chatsMessageUnavailable)
                return@withContext
            }

            remoteViews.setOnClickPendingIntent(
                R.id.promptBarContainer,
                intents.launchDuckAi(context, DuckAiWidgetTarget.PROMPT_BAR.requestCode, state.newChatUrl, widgetLaunch),
            )
            wireActions(context, remoteViews, state)
            if (showChats) {
                wireViewAllChats(context, remoteViews, state.chatHistoryAvailable)
                renderChats(context, appWidgetId, remoteViews, state)
            }
        }
    }

    private class Loaded(
        val showChats: Boolean,
        val state: State?,
    )

    private suspend fun loadState(showChats: Boolean): State {
        val historyAvailable = showChats && duckChat.isChatHistoryAvailable()
        val widgetChats = if (historyAvailable) runCatching { chats.getChats() }.getOrDefault(emptyList()) else emptyList()
        // Built once: the URL builders consume the one-shot address bar picker attribution.
        val newChatUrl = duckChat.getDuckChatUrl("", false)
        return State(
            newChatUrl = newChatUrl,
            voiceUrl = duckChat.getVoiceDuckChatUrl(),
            chatHistoryAvailable = historyAvailable,
            chats = widgetChats,
            chatUrls = widgetChats.associate { it.chatId to duckChat.buildChatUrl(it.chatId) },
        )
    }

    private fun wireActions(
        context: Context,
        remoteViews: RemoteViews,
        state: State,
    ) {
        remoteViews.setOnClickPendingIntent(
            R.id.actionVoice,
            intents.launchDuckAi(context, DuckAiWidgetTarget.VOICE.requestCode, state.voiceUrl, widgetLaunch),
        )
        remoteViews.setOnClickPendingIntent(
            R.id.actionCreateImage,
            intents.launchDuckAi(
                context,
                DuckAiWidgetTarget.CREATE_IMAGE.requestCode,
                state.newChatUrl,
                DuckChatLaunch.ImageGeneration(DuckChatLaunchOrigin.HOME_SCREEN_WIDGET),
            ),
        )
        remoteViews.setOnClickPendingIntent(
            R.id.actionTakePhoto,
            intents.launchDuckAi(
                context,
                DuckAiWidgetTarget.TAKE_PHOTO.requestCode,
                state.newChatUrl,
                DuckChatLaunch.CameraCapture(DuckChatLaunchOrigin.HOME_SCREEN_WIDGET),
            ),
        )
    }

    private fun wireViewAllChats(
        context: Context,
        remoteViews: RemoteViews,
        chatHistoryAvailable: Boolean,
    ) {
        if (chatHistoryAvailable) {
            remoteViews.setViewVisibility(R.id.chatsViewAllContainer, View.VISIBLE)
            remoteViews.setOnClickPendingIntent(R.id.chatsViewAll, intents.openChatHistory(context, DuckAiWidgetTarget.VIEW_ALL_CHATS.requestCode))
        } else {
            remoteViews.setViewVisibility(R.id.chatsViewAllContainer, View.GONE)
        }
    }

    private fun renderChats(
        context: Context,
        appWidgetId: Int,
        remoteViews: RemoteViews,
        state: State,
    ) {
        when {
            !state.chatHistoryAvailable -> showMessage(remoteViews, R.id.chatsMessageUnavailable)
            state.chats.isEmpty() -> showMessage(remoteViews, R.id.chatsMessageEmpty)
            else -> showChats(context, appWidgetId, remoteViews, state)
        }
    }

    private fun showMessage(
        remoteViews: RemoteViews,
        messageViewId: Int,
    ) {
        remoteViews.setViewVisibility(R.id.chatsList, View.GONE)
        remoteViews.setViewVisibility(R.id.chatsMessageEmpty, if (messageViewId == R.id.chatsMessageEmpty) View.VISIBLE else View.GONE)
        remoteViews.setViewVisibility(R.id.chatsMessageUnavailable, if (messageViewId == R.id.chatsMessageUnavailable) View.VISIBLE else View.GONE)
    }

    private fun showChats(
        context: Context,
        appWidgetId: Int,
        remoteViews: RemoteViews,
        state: State,
    ) {
        remoteViews.setViewVisibility(R.id.chatsMessageEmpty, View.GONE)
        remoteViews.setViewVisibility(R.id.chatsMessageUnavailable, View.GONE)
        remoteViews.setViewVisibility(R.id.chatsList, View.VISIBLE)

        val items = RemoteViewsCompat.RemoteCollectionItems.Builder().setHasStableIds(true)
        state.chats.forEach { items.addItem(it.chatId.hashCode().toLong(), chatView(context, it, state.chatUrls.getValue(it.chatId))) }
        RemoteViewsCompat.setRemoteAdapter(context, remoteViews, appWidgetId, R.id.chatsList, items.build())
        remoteViews.setPendingIntentTemplate(
            R.id.chatsList,
            intents.chatClickTemplate(context, DuckAiWidgetTarget.CHAT.requestCode, state.newChatUrl),
        )
    }

    private fun chatView(
        context: Context,
        chat: DuckAiChatsWidgetChat,
        chatUrl: String,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.duck_ai_widget_item_chat).apply {
        setTextViewText(R.id.chatsItemTitle, chat.title)
        setImageViewResource(R.id.chatsItemIcon, chat.type.widgetIconRes(chat.pinned))
        setOnClickFillInIntent(R.id.chatsItemContainer, intents.chatFillInIntent(context, chatUrl))
    }

    private class State(
        val newChatUrl: String,
        val voiceUrl: String,
        val chatHistoryAvailable: Boolean,
        val chats: List<DuckAiChatsWidgetChat>,
        val chatUrls: Map<String, String>,
    )
}
