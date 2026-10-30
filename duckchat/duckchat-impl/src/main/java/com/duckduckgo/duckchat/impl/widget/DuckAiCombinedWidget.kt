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
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.codegen.annotations.InjectWith
import com.duckduckgo.di.android.AndroidInjection
import com.duckduckgo.di.scopes.ReceiverScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@InjectWith(ReceiverScope::class)
class DuckAiCombinedWidget : AppWidgetProvider() {

    @Inject
    lateinit var duckAiCombinedWidgetConfigurator: DuckAiCombinedWidgetConfigurator

    @Inject
    lateinit var pixel: Pixel

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        AndroidInjection.inject(this, context)
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        pixel.fire(DuckChatPixelName.DUCK_AI_COMBINED_WIDGET_ADDED)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        logcat { "DuckAiCombinedWidget onUpdate" }
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        updateAppWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val views = RemoteViews(context.packageName, R.layout.duck_ai_combined_widget_daynight)

        appCoroutineScope.launch {
            duckAiCombinedWidgetConfigurator.populateRemoteViews(
                context = context,
                remoteViews = views,
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
            logcat { "DuckAiCombinedWidget updateAppWidget completed for widget id = $appWidgetId" }
        }
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        pixel.fire(DuckChatPixelName.DUCK_AI_COMBINED_WIDGET_DELETED)
    }
}
