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
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
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

/**
 * Widget height from which the recent chats list is shown. Two launcher rows host up to ~230dp (the host view includes padding
 * and exceeds the reported option sizes), three rows ~350dp; this fits the prompt bar, pills, two chats and the footer in between.
 */
const val MIN_CHATS_HEIGHT_DP = 280

/**
 * Widget width from which the action pills show their labels next to the icons; narrower widgets show icons only.
 * Three equal-width pills need ~93dp each for the widest label ("Camera", ~48dp text at 14sp, plus the icon and 8dp paddings and gaps),
 * so ~325dp of host width including the row padding. Launchers report ~7dp more than the host view, hence 336dp (a 4-column widget
 * reported 342dp on a 384dp-wide phone; narrower phones fall back to icons only instead of truncating the labels).
 */
const val MIN_LABELS_WIDTH_DP = 336

private class ActionPill(val pillId: Int, val labelId: Int, val labelRes: Int)

private val ACTION_PILLS = listOf(
    ActionPill(R.id.actionVoice, R.id.labelVoice, R.string.duckAiWidgetVoiceAction),
    ActionPill(R.id.actionCreateImage, R.id.labelCreateImage, R.string.duckAiWidgetImageAction),
    ActionPill(R.id.actionTakePhoto, R.id.labelTakePhoto, R.string.duckAiWidgetCameraAction),
)

private const val MIN_WIDTH_DP = 180f
private const val MIN_HEIGHT_DP = 110f

class DuckAiWidgetConfigurator @Inject constructor(
    private val duckChat: DuckChatInternal,
    private val chats: DuckAiChatsWidgetChats,
    private val widgetPreferences: DuckAiWidgetPreferences,
    private val intents: DuckAiWidgetIntents,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val widgetLaunch = DuckChatLaunch.Open(DuckChatLaunchOrigin.HOME_SCREEN_WIDGET)

    suspend fun createRemoteViews(
        context: Context,
        appWidgetId: Int,
        heightDp: Int,
        widthDp: Int = Int.MAX_VALUE,
    ): RemoteViews {
        val loaded = load(appWidgetId)
        if (Build.VERSION.SDK_INT >= 31) {
            suspend fun variant(showLabels: Boolean, showChats: Boolean) =
                RemoteViews(context.packageName, R.layout.duck_ai_widget_daynight).also {
                    render(
                        context = context,
                        appWidgetId = appWidgetId,
                        remoteViews = it,
                        loaded = loaded,
                        showChats = showChats,
                        showLabels = showLabels,
                    )
                }

            val variants = buildMap {
                put(SizeF(MIN_WIDTH_DP, MIN_HEIGHT_DP), variant(showLabels = false, showChats = false))
                if (loaded.showChats) {
                    put(SizeF(MIN_WIDTH_DP, MIN_CHATS_HEIGHT_DP.toFloat()), variant(showLabels = false, showChats = true))
                }
                put(SizeF(MIN_LABELS_WIDTH_DP.toFloat(), MIN_HEIGHT_DP), variant(showLabels = true, showChats = false))
                if (loaded.showChats) {
                    put(
                        SizeF(MIN_LABELS_WIDTH_DP.toFloat(), MIN_CHATS_HEIGHT_DP.toFloat()),
                        variant(showLabels = true, showChats = true),
                    )
                }
            }
            return RemoteViews(variants)
        }
        return RemoteViews(context.packageName, R.layout.duck_ai_widget_daynight).also {
            render(
                context = context,
                appWidgetId = appWidgetId,
                remoteViews = it,
                loaded = loaded,
                showChats = loaded.showChats && heightDp >= MIN_CHATS_HEIGHT_DP,
                showLabels = widthDp >= MIN_LABELS_WIDTH_DP,
            )
        }
    }

    suspend fun populateRemoteViews(
        context: Context,
        appWidgetId: Int,
        remoteViews: RemoteViews,
        heightDp: Int = Int.MAX_VALUE,
        widthDp: Int = Int.MAX_VALUE,
    ) {
        val loaded = load(appWidgetId)
        render(
            context = context,
            appWidgetId = appWidgetId,
            remoteViews = remoteViews,
            loaded = loaded,
            showChats = loaded.showChats && heightDp >= MIN_CHATS_HEIGHT_DP,
            showLabels = widthDp >= MIN_LABELS_WIDTH_DP,
        )
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
        showLabels: Boolean,
    ) {
        val state = loaded.state
        withContext(dispatcherProvider.main()) {
            if (Build.VERSION.SDK_INT >= 31) fitCardToContent(remoteViews, !showChats)
            showActionLabels(context, remoteViews, showLabels)
            remoteViews.setViewVisibility(R.id.chatsSection, if (showChats) View.VISIBLE else View.GONE)
            if (state == null) {
                logcat { "DuckAiWidgetProvider Duck.ai disabled, click targets not wired" }
                remoteViews.setViewVisibility(R.id.chatsViewAllContainer, View.GONE)
                showMessage(remoteViews, R.id.chatsMessageUnavailable)
                return@withContext
            }

            remoteViews.setOnClickPendingIntent(
                R.id.promptBarContainer,
                intents.launchDuckAi(context, DuckAiWidgetTarget.PROMPT_BAR.requestCode, null, widgetLaunch),
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

    @RequiresApi(31)
    private fun fitCardToContent(remoteViews: RemoteViews, fit: Boolean) {
        val height = if (fit) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT
        remoteViews.setViewLayoutHeight(R.id.duckAiWidgetContainer, height.toFloat(), TypedValue.COMPLEX_UNIT_PX)
    }

    /** Icon-only pills stay accessible through the pill's content description; with labels shown the label itself is announced. */
    private fun showActionLabels(
        context: Context,
        remoteViews: RemoteViews,
        showLabels: Boolean,
    ) {
        ACTION_PILLS.forEach { pill ->
            remoteViews.setViewVisibility(pill.labelId, if (showLabels) View.VISIBLE else View.GONE)
            remoteViews.setContentDescription(pill.pillId, if (showLabels) null else context.getString(pill.labelRes))
        }
    }

    private suspend fun loadState(showChats: Boolean): State {
        val historyAvailable = showChats && duckChat.isChatHistoryAvailable()
        val widgetChats = if (historyAvailable) runCatching { chats.getChats() }.getOrDefault(emptyList()) else emptyList()
        return State(
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
            intents.launchDuckAi(
                context,
                DuckAiWidgetTarget.VOICE.requestCode,
                null,
                DuckChatLaunch.Voice(DuckChatLaunchOrigin.HOME_SCREEN_WIDGET),
            ),
        )
        remoteViews.setOnClickPendingIntent(
            R.id.actionCreateImage,
            intents.launchDuckAi(
                context,
                DuckAiWidgetTarget.CREATE_IMAGE.requestCode,
                null,
                DuckChatLaunch.ImageGeneration(DuckChatLaunchOrigin.HOME_SCREEN_WIDGET),
            ),
        )
        remoteViews.setOnClickPendingIntent(
            R.id.actionTakePhoto,
            intents.launchDuckAi(
                context,
                DuckAiWidgetTarget.TAKE_PHOTO.requestCode,
                null,
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
            intents.chatClickTemplate(context, DuckAiWidgetTarget.CHAT.requestCode),
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
        val chatHistoryAvailable: Boolean,
        val chats: List<DuckAiChatsWidgetChat>,
        val chatUrls: Map<String, String>,
    )
}
