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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.app.tabs.DuckChatLaunchAction
import com.duckduckgo.app.tabs.DuckChatLaunchOrigin
import com.duckduckgo.duckchat.api.DuckChatHistoryParams
import com.duckduckgo.duckchat.api.DuckChatHistorySource
import com.duckduckgo.navigation.api.GlobalActivityStarter
import javax.inject.Inject

class DuckAiWidgetIntents @Inject constructor(
    private val browserNav: BrowserNav,
    private val globalActivityStarter: GlobalActivityStarter,
) {

    fun launchDuckAi(
        context: Context,
        requestCode: Int,
        duckChatUrl: String,
        launchAction: DuckChatLaunchAction? = null,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        openDuckChat(context, duckChatUrl, launchAction).asViewIntent(),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun openChatHistory(context: Context, requestCode: Int): PendingIntent? {
        val intent = globalActivityStarter.startIntent(context, DuckChatHistoryParams(DuckChatHistorySource.WIDGET)) ?: return null
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * Template for list items; each item's fill-in intent, built by [chatFillInIntent], supplies every extra.
     * [Intent.fillIn] never overwrites an extra already present, so the template must carry none.
     */
    fun chatClickTemplate(context: Context, requestCode: Int, newChatUrl: String): PendingIntent {
        val mutability = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getActivity(
            context,
            requestCode,
            openDuckChat(context, newChatUrl).replaceExtras(null).asViewIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or mutability,
        )
    }

    fun chatFillInIntent(context: Context, chatUrl: String): Intent =
        Intent().putExtras(openDuckChat(context, chatUrl).extras!!)

    private fun openDuckChat(context: Context, duckChatUrl: String, launchAction: DuckChatLaunchAction? = null): Intent =
        browserNav.openDuckChat(
            context,
            duckChatUrl = duckChatUrl,
            launchAction = launchAction,
            launchOrigin = DuckChatLaunchOrigin.HOME_SCREEN_WIDGET,
        )

    private fun Intent.asViewIntent(): Intent = apply { action = Intent.ACTION_VIEW }
}
