/*
 * Copyright (c) 2018 DuckDuckGo
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
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoApplication
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.WIDGET_ADDED
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.WIDGET_DELETED
import com.duckduckgo.app.widget.ui.AppWidgetCapabilities


class SearchWidgetLight : SearchWidget(R.layout.search_widget_light)

open class SearchWidget(val layoutId: Int = R.layout.search_widget) : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        val application = context.applicationContext as? DuckDuckGoApplication ?: return
        if (!application.appInstallStore.widgetInstalled) {
            application.appInstallStore.widgetInstalled = true
            application.pixel.fire(WIDGET_ADDED)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, layoutId)
        views.setOnClickPendingIntent(R.id.widgetContainer, buildPendingIntent(context))
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = BrowserActivity.intent(context, widgetSearch = true)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray?) {
        val application = context.applicationContext as? DuckDuckGoApplication ?: return
        val widgetCapabilities = AppWidgetCapabilities(context)
        if (application.appInstallStore.widgetInstalled && !widgetCapabilities.hasInstalledWidgets) {
            application.appInstallStore.widgetInstalled = false
            application?.pixel?.fire(WIDGET_DELETED)
        }
    }
}