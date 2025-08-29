/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.systemsearch.SystemSearchActivity
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.voice.api.VoiceSearchAvailability
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.logcat

class SearchWidgetConfigurator @Inject constructor(
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val duckChat: DuckChat,
    private val dispatcherProvider: DispatcherProvider,
) {

    suspend fun populateRemoteViews(
        context: Context,
        remoteViews: RemoteViews,
        fromFavWidget: Boolean,
        fromSearchOnlyWidget: Boolean = false,
    ) {
        val (voiceSearchEnabled, duckAiEnabled) = withContext(dispatcherProvider.io()) {
            voiceSearchAvailability.isVoiceSearchAvailable to (duckChat.isEnabled() && duckChat.wasOpenedBefore())
        }

        logcat { "SearchWidgetConfigurator voiceSearchEnabled=$voiceSearchEnabled, duckAiEnabled=$duckAiEnabled, searchOnly=$fromSearchOnlyWidget" }

        val showDuckAi = !fromSearchOnlyWidget && duckAiEnabled
        withContext(dispatcherProvider.main()) {
            remoteViews.setViewVisibility(R.id.voiceSearch, if (voiceSearchEnabled) View.VISIBLE else View.GONE)
            remoteViews.setViewVisibility(R.id.duckAi, if (showDuckAi) View.VISIBLE else View.GONE)
            remoteViews.setViewVisibility(R.id.separator, if (voiceSearchEnabled && showDuckAi) View.VISIBLE else View.GONE)
            remoteViews.setViewVisibility(R.id.search, if (!voiceSearchEnabled && !showDuckAi) View.VISIBLE else View.GONE)

            if (voiceSearchEnabled) {
                val pendingIntent = buildVoiceSearchPendingIntent(context, fromFavWidget)
                remoteViews.setOnClickPendingIntent(R.id.voiceSearch, pendingIntent)
            }

            if (showDuckAi) {
                val pendingIntent = buildDuckAiPendingIntent(context)
                remoteViews.setOnClickPendingIntent(R.id.duckAi, pendingIntent)
            }
        }
    }

    private fun buildVoiceSearchPendingIntent(
        context: Context,
        fromFavWidget: Boolean,
    ): PendingIntent {
        val intent = if (fromFavWidget) SystemSearchActivity.fromFavWidget(context, true) else SystemSearchActivity.fromWidget(context, true)
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun buildDuckAiPendingIntent(
        context: Context,
    ): PendingIntent {
        val intent = BrowserActivity.intent(context, openDuckChat = true).also { it.action = Intent.ACTION_VIEW }
        return PendingIntent.getActivity(context, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
