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
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoApplication
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.pixels.AppPixelName.WIDGETS_ADDED
import com.duckduckgo.app.pixels.AppPixelName.WIDGETS_DELETED
import com.duckduckgo.app.systemsearch.SystemSearchActivity
import com.duckduckgo.app.voice.VoiceSearchLauncher
import com.duckduckgo.app.widget.ui.AppWidgetCapabilities
import javax.inject.Inject

class SearchWidgetLight : SearchWidget(R.layout.search_widget_light)

open class SearchWidget(val layoutId: Int = R.layout.search_widget) : AppWidgetProvider() {

    @Inject
    lateinit var appInstallStore: AppInstallStore

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var widgetCapabilities: AppWidgetCapabilities

    @Inject
    lateinit var voiceSearchLauncher: VoiceSearchLauncher

    override fun onReceive(
        context: Context,
        intent: Intent?
    ) {
        inject(context)
        super.onReceive(context, intent)
    }

    private fun inject(context: Context) {
        val application = context.applicationContext as DuckDuckGoApplication
        application.daggerAppComponent.inject(this)
    }

    override fun onEnabled(context: Context) {
        if (!appInstallStore.widgetInstalled) {
            appInstallStore.widgetInstalled = true
            pixel.fire(WIDGETS_ADDED)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, null)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        updateAppWidget(context, appWidgetManager, appWidgetId, newOptions)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        val appWidgetOptions = appWidgetManager.getAppWidgetOptions(appWidgetId)
        var portraitWidth = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)

        if (newOptions != null) {
            portraitWidth = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        }

        val shouldShowHint = shouldShowSearchBarHint(portraitWidth)

        val views = RemoteViews(context.packageName, layoutId)
        views.setViewVisibility(R.id.searchInputBox, if (shouldShowHint) View.VISIBLE else View.INVISIBLE)
        views.setOnClickPendingIntent(R.id.widgetContainer, buildPendingIntent(context))

        configureVoiceSearch(context, views, false)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun shouldShowSearchBarHint(portraitWidth: Int): Boolean {
        return portraitWidth > SEARCH_BAR_MIN_HINT_WIDTH_SIZE
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = SystemSearchActivity.fromWidget(context)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray?
    ) {
        if (appInstallStore.widgetInstalled && !widgetCapabilities.hasInstalledWidgets) {
            appInstallStore.widgetInstalled = false
            pixel.fire(WIDGETS_DELETED)
        }
    }

    companion object {
        private const val SEARCH_BAR_MIN_HINT_WIDTH_SIZE = 168
    }
}
