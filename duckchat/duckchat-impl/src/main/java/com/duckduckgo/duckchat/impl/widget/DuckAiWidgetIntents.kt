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
import com.duckduckgo.app.tabs.BrowserNav
import com.duckduckgo.app.tabs.DuckChatLaunch
import javax.inject.Inject

class DuckAiWidgetIntents @Inject constructor(
    private val browserNav: BrowserNav,
) {

    fun launchDuckAi(
        context: Context,
        requestCode: Int,
        duckChatUrl: String,
        launch: DuckChatLaunch,
    ): PendingIntent = PendingIntent.getActivity(
        context,
        requestCode,
        browserNav.openDuckChat(
            context,
            duckChatUrl = duckChatUrl,
            launch = launch,
        ).asViewIntent(),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun Intent.asViewIntent(): Intent = apply { action = Intent.ACTION_VIEW }
}
