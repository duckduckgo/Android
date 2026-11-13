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
import android.widget.RemoteViews
import com.duckduckgo.app.tabs.DuckChatLaunch
import com.duckduckgo.app.tabs.DuckChatLaunchOrigin
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.R
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

/** Request codes of the widget's tap targets; each target needs its own so their [android.app.PendingIntent]s stay distinct. */
enum class DuckAiWidgetTarget(val requestCode: Int) {
    PROMPT_BAR(1591),
    VOICE(1592),
    CREATE_IMAGE(1593),
    TAKE_PHOTO(1594),
}

class DuckAiWidgetConfigurator @Inject constructor(
    private val duckChat: DuckChatInternal,
    private val intents: DuckAiWidgetIntents,
    private val dispatcherProvider: DispatcherProvider,
) {
    private val widgetLaunch = DuckChatLaunch.Open(DuckChatLaunchOrigin.HOME_SCREEN_WIDGET)

    suspend fun populateRemoteViews(
        context: Context,
        remoteViews: RemoteViews,
    ) {
        val enabled = withContext(dispatcherProvider.io()) { duckChat.isEnabled() }

        withContext(dispatcherProvider.main()) {
            if (!enabled) {
                logcat { "DuckAiWidgetProvider Duck.ai disabled, click targets not wired" }
                return@withContext
            }

            remoteViews.setOnClickPendingIntent(
                R.id.promptBarContainer,
                intents.launchDuckAi(context, DuckAiWidgetTarget.PROMPT_BAR.requestCode, null, widgetLaunch),
            )
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
    }
}
