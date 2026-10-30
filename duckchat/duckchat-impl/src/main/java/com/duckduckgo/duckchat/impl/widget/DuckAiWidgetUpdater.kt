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

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import javax.inject.Inject

/** Re-renders the placed Duck.ai widget when the remote config changes, since that can enable or disable Duck.ai. */
@ContributesIntoSet(
    scope = AppScope::class,
    binding = binding<PrivacyConfigCallbackPlugin>(),
)
class DuckAiWidgetUpdater @Inject constructor(
    private val context: Context,
) : PrivacyConfigCallbackPlugin {

    override fun onPrivacyConfigDownloaded() {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, DuckAiCombinedWidget::class.java))?.takeIf { it.isNotEmpty() } ?: return

        val intent = Intent(context, DuckAiCombinedWidget::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        context.sendBroadcast(intent)
    }
}
