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
import com.duckduckgo.widget.api.HomeScreenWidgetLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@InjectWith(ReceiverScope::class)
class DuckAiWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var duckAiWidgetConfigurator: DuckAiWidgetConfigurator

    @Inject
    lateinit var duckAiWidgetUpdater: DuckAiWidgetUpdater

    @Inject
    lateinit var widgetPreferences: DuckAiWidgetPreferences

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var homeScreenWidgetLifecycle: HomeScreenWidgetLifecycle

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
        pixel.fire(DuckChatPixelName.DUCK_AI_WIDGET_ADDED)
        homeScreenWidgetLifecycle.onWidgetEnabled()
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        logcat { "DuckAiWidgetProvider onUpdate" }
        duckAiWidgetUpdater.startObservingIfNeeded()
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
        val views = RemoteViews(context.packageName, R.layout.duck_ai_widget_daynight)

        appCoroutineScope.launch {
            duckAiWidgetConfigurator.populateRemoteViews(
                context = context,
                appWidgetId = appWidgetId,
                remoteViews = views,
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
            logcat { "DuckAiWidgetProvider updateAppWidget completed for widget id = $appWidgetId" }
        }
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        super.onDeleted(context, appWidgetIds)
        appCoroutineScope.launch { widgetPreferences.remove(appWidgetIds) }
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        pixel.fire(DuckChatPixelName.DUCK_AI_WIDGET_DELETED)
        homeScreenWidgetLifecycle.onWidgetDisabled()
    }
}
