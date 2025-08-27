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
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DuckDuckGoApplication
import com.duckduckgo.app.pixels.AppPixelName.SEARCH_ONLY_WIDGET_ADDED
import com.duckduckgo.app.pixels.AppPixelName.SEARCH_ONLY_WIDGET_DELETED
import com.duckduckgo.app.systemsearch.SystemSearchActivity
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat

class SearchOnlyWidget(val layoutId: Int = R.layout.search_only_widget_daynight) : AppWidgetProvider() {

    @Inject
    lateinit var searchWidgetConfigurator: SearchWidgetConfigurator

    @Inject
    lateinit var searchWidgetLifecycleDelegate: SearchWidgetLifecycleDelegate

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        inject(context)
        super.onReceive(context, intent)
    }

    private fun inject(context: Context) {
        val application = context.applicationContext as DuckDuckGoApplication
        application.daggerAppComponent.inject(this)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        appCoroutineScope.launch {
            searchWidgetLifecycleDelegate.handleOnWidgetEnabled(SEARCH_ONLY_WIDGET_ADDED)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        logcat { "SearchOnlyWidget onUpdate" }
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            logcat { "SearchOnlyWidget onUpdate called for widget id = $appWidgetId" }
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        logcat { "SearchOnlyWidget onAppWidgetOptionsChanged called for widget id = $appWidgetId" }
        updateAppWidget(context, appWidgetManager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        logcat { "SearchOnlyWidget updateAppWidget called for widget id = $appWidgetId" }

        val views = RemoteViews(context.packageName, layoutId)
        views.setOnClickPendingIntent(R.id.widgetContainer, buildPendingIntent(context))

        appCoroutineScope.launch {
            searchWidgetConfigurator.populateRemoteViews(
                context = context,
                remoteViews = views,
                fromFavWidget = false,
                fromSearchOnlyWidget = true,
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
            logcat { "SearchOnlyWidget updateAppWidget completed for widget id = $appWidgetId" }
        }
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = SystemSearchActivity.fromWidget(context)
        return PendingIntent.getActivity(
            context,
            SEARCH_ONLY_WIDGET_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        searchWidgetLifecycleDelegate.handleOnWidgetDisabled(SEARCH_ONLY_WIDGET_DELETED)
    }

    companion object {
        private const val SEARCH_ONLY_WIDGET_REQUEST_CODE = 1550
    }
}
