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
import android.content.res.ColorStateList
import android.graphics.BlendMode
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.RequiresApi
import androidx.core.widget.RemoteViewsCompat
import com.duckduckgo.app.tabs.DuckChatLaunch
import com.duckduckgo.app.tabs.DuckChatLaunchOrigin
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.models.widgetIconRes
import com.duckduckgo.duckchat.impl.repository.DuckChatFeatureRepository
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
private const val MIN_HEIGHT_DP = 142f

class DuckAiWidgetConfigurator @Inject constructor(
    private val duckChatFeature: DuckChatFeature,
    private val repository: DuckChatFeatureRepository,
    private val duckChat: DuckChatInternal,
    private val chats: DuckAiChatsWidgetChats,
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
        val loaded = load()
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
                if (loaded.state.hasChats()) {
                    put(SizeF(MIN_WIDTH_DP, MIN_CHATS_HEIGHT_DP.toFloat()), variant(showLabels = false, showChats = true))
                }
                put(SizeF(MIN_LABELS_WIDTH_DP.toFloat(), MIN_HEIGHT_DP), variant(showLabels = true, showChats = false))
                if (loaded.state.hasChats()) {
                    put(
                        SizeF(MIN_LABELS_WIDTH_DP.toFloat(), MIN_CHATS_HEIGHT_DP.toFloat()),
                        variant(showLabels = true, showChats = true),
                    )
                }
            }
            return RemoteViews(variants)
        }
        val showChats = loaded.state.hasChats() && heightDp >= MIN_CHATS_HEIGHT_DP
        val layout = if (showChats) R.layout.duck_ai_widget_daynight else R.layout.duck_ai_widget_compact_daynight
        return RemoteViews(context.packageName, layout).also {
            render(
                context = context,
                appWidgetId = appWidgetId,
                remoteViews = it,
                loaded = loaded,
                showChats = showChats,
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
        val loaded = load()
        render(
            context = context,
            appWidgetId = appWidgetId,
            remoteViews = remoteViews,
            loaded = loaded,
            showChats = loaded.state.hasChats() && heightDp >= MIN_CHATS_HEIGHT_DP,
            showLabels = widthDp >= MIN_LABELS_WIDTH_DP,
        )
    }

    private suspend fun load(): Loaded {
        val colors = withContext(dispatcherProvider.io()) { widgetColors() }
        val state = withContext(dispatcherProvider.io()) {
            if (!(duckChatFeature.self().isEnabled() && repository.isDuckChatUserEnabled())) return@withContext null
            loadState(colors)
        }
        return Loaded(colors, state)
    }

    private fun State?.hasChats() = this != null && chats.isNotEmpty()

    private suspend fun render(
        context: Context,
        appWidgetId: Int,
        remoteViews: RemoteViews,
        loaded: Loaded,
        showChats: Boolean,
        showLabels: Boolean,
    ) {
        val colors = loaded.colors
        val state = loaded.state
        withContext(dispatcherProvider.main()) {
            if (colors != null && Build.VERSION.SDK_INT >= 31) applyColors(remoteViews, colors)
            if (Build.VERSION.SDK_INT >= 31) fitCardToContent(remoteViews, !showChats)
            showActionLabels(context, remoteViews, showLabels)
            remoteViews.setViewVisibility(R.id.chatsSection, if (showChats) View.VISIBLE else View.GONE)
            if (state == null) {
                logcat { "DuckAiWidgetProvider Duck.ai disabled, click targets not wired" }
                return@withContext
            }

            remoteViews.setOnClickPendingIntent(
                R.id.promptBarContainer,
                intents.launchDuckAi(context, DuckAiWidgetTarget.PROMPT_BAR.requestCode, null, widgetLaunch),
            )
            wireActions(context, remoteViews)
            if (showChats) {
                wireViewAllChats(context, remoteViews)
                showChats(context, appWidgetId, remoteViews, state)
            }
        }
    }

    private class Loaded(
        val colors: WidgetColors?,
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

    private fun widgetColors(): WidgetColors? = when {
        Build.VERSION.SDK_INT < 31 -> null
        duckChatFeature.duckAiWidgetDynamicColors().isEnabled() -> WidgetColors.DYNAMIC
        else -> WidgetColors.STATIC
    }

    // Always set on SDK 31+: the launcher reapplies RemoteViews onto the views it already inflated, so omitting a tint
    // would leave the previous one in place. A null background colour clears the tint, restoring the drawable's own colour.
    // SRC makes the tint replace the translucent drawable colours instead of inheriting their alpha (default SRC_IN).
    // A null blend mode can't be parcelled, so clearing restores SRC_IN explicitly.
    @RequiresApi(31)
    private fun applyColors(remoteViews: RemoteViews, colors: WidgetColors) {
        remoteViews.tint(R.id.duckAiWidgetContainer, colors.background)
        remoteViews.tint(R.id.promptBarContainer, colors.searchBar)
        listOf(R.id.actionVoice, R.id.actionCreateImage, R.id.actionTakePhoto).forEach {
            remoteViews.tint(it, colors.container)
        }
        listOf(R.id.iconVoice, R.id.iconImage, R.id.iconPhoto).forEach {
            remoteViews.tintImage(it, colors.onContainer)
        }
        listOf(R.id.labelVoice, R.id.labelCreateImage, R.id.labelTakePhoto).forEach {
            remoteViews.tintText(it, colors.onContainer)
        }
        remoteViews.tintText(R.id.promptBarHint, colors.hint)
        remoteViews.tintText(R.id.chatsViewAll, colors.text)
        listOf(R.id.dividerTop, R.id.dividerBottom).forEach {
            remoteViews.tint(it, colors.divider)
        }
    }

    @RequiresApi(31)
    private fun applyChatColors(remoteViews: RemoteViews, colors: WidgetColors) {
        remoteViews.tint(R.id.chatsItemIcon, colors.container)
        remoteViews.tintImage(R.id.chatsItemIcon, colors.onContainer)
        remoteViews.tintText(R.id.chatsItemTitle, colors.text)
    }

    @RequiresApi(31)
    private fun RemoteViews.tint(viewId: Int, @ColorRes color: Int?) {
        if (color != null) {
            setColorStateList(viewId, "setBackgroundTintList", color)
            setBlendMode(viewId, "setBackgroundTintBlendMode", BlendMode.SRC)
        } else {
            setColorStateList(viewId, "setBackgroundTintList", null as ColorStateList?)
            setBlendMode(viewId, "setBackgroundTintBlendMode", BlendMode.SRC_IN)
        }
    }

    @RequiresApi(31)
    private fun RemoteViews.tintImage(viewId: Int, @ColorRes color: Int) = setColorStateList(viewId, "setImageTintList", color)

    @RequiresApi(31)
    private fun RemoteViews.tintText(viewId: Int, @ColorRes color: Int) = setColorStateList(viewId, "setTextColor", color)

    private suspend fun loadState(colors: WidgetColors?): State {
        val widgetChats = if (duckChat.isChatHistoryAvailable()) runCatching { chats.getChats() }.getOrDefault(emptyList()) else emptyList()
        return State(
            colors = colors,
            chats = widgetChats,
            chatUrls = widgetChats.associate { it.chatId to duckChat.buildChatUrlWithoutAttribution(it.chatId) },
        )
    }

    private fun wireActions(
        context: Context,
        remoteViews: RemoteViews,
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
    ) {
        remoteViews.setOnClickPendingIntent(R.id.chatsViewAll, intents.openChatHistory(context, DuckAiWidgetTarget.VIEW_ALL_CHATS.requestCode))
    }

    private fun showChats(
        context: Context,
        appWidgetId: Int,
        remoteViews: RemoteViews,
        state: State,
    ) {
        val items = RemoteViewsCompat.RemoteCollectionItems.Builder().setHasStableIds(true)
        state.chats.forEach {
            items.addItem(
                it.chatId.hashCode().toLong(),
                chatView(context, it, state.chatUrls.getValue(it.chatId), state.colors),
            )
        }
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
        colors: WidgetColors?,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.duck_ai_widget_item_chat).apply {
        if (colors != null && Build.VERSION.SDK_INT >= 31) applyChatColors(this, colors)
        setTextViewText(R.id.chatsItemTitle, chat.title)
        setImageViewResource(R.id.chatsItemIcon, chat.type.widgetIconRes(chat.pinned))
        setOnClickFillInIntent(R.id.chatsItemContainer, intents.chatFillInIntent(context, chatUrl))
    }

    private class State(
        val colors: WidgetColors?,
        val chats: List<DuckAiChatsWidgetChat>,
        val chatUrls: Map<String, String>,
    )

    private enum class WidgetColors(
        @ColorRes val background: Int?,
        @ColorRes val searchBar: Int?,
        @ColorRes val container: Int?,
        @ColorRes val divider: Int?,
        @ColorRes val hint: Int,
        @ColorRes val text: Int,
        @ColorRes val onContainer: Int,
    ) {
        DYNAMIC(
            R.color.duckAiWidgetDynamicBackgroundColor,
            R.color.duckAiWidgetDynamicSearchBarColor,
            R.color.duckAiWidgetDynamicContainerColor,
            R.color.duckAiWidgetDynamicDividerColor,
            R.color.duckAiWidgetDynamicHintColor,
            R.color.duckAiWidgetDynamicTextColor,
            R.color.duckAiWidgetDynamicOnContainerColor,
        ),
        STATIC(
            null,
            null,
            null,
            null,
            R.color.duckAiWidgetHintColor,
            R.color.duckAiWidgetTextColor,
            R.color.duckAiWidgetOnContainerColor,
        ),
    }
}
