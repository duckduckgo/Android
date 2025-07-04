/*
 * Copyright (c) 2021 DuckDuckGo
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
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DuckDuckGoApplication
import com.duckduckgo.app.pixels.AppPixelName.SEARCH_AND_FAVORITES_WIDGET_ADDED
import com.duckduckgo.app.pixels.AppPixelName.SEARCH_AND_FAVORITES_WIDGET_DELETED
import com.duckduckgo.app.systemsearch.SystemSearchActivity
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.widget.FavoritesWidgetService.Companion.THEME_EXTRAS
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.logcat

enum class WidgetTheme {
    LIGHT,
    DARK,
    SYSTEM_DEFAULT,
    ;

    companion object {
        fun getThemeFrom(value: String?): WidgetTheme {
            if (value.isNullOrEmpty()) return SYSTEM_DEFAULT
            return runCatching { valueOf(value) }.getOrDefault(SYSTEM_DEFAULT)
        }
    }
}

class SearchAndFavoritesWidget : AppWidgetProvider() {

    @Inject
    lateinit var widgetPrefs: WidgetPreferences

    @Inject
    lateinit var gridCalculator: SearchAndFavoritesGridCalculator

    @Inject
    lateinit var voiceSearchWidgetConfigurator: VoiceSearchWidgetConfigurator

    @Inject
    lateinit var appBuildConfig: AppBuildConfig

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var searchWidgetLifecycleDelegate: SearchWidgetLifecycleDelegate

    private var layoutId: Int = R.layout.search_favorites_widget_daynight_auto

    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        inject(context)
        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        appCoroutineScope.launch {
            searchWidgetLifecycleDelegate.handleOnWidgetEnabled(SEARCH_AND_FAVORITES_WIDGET_ADDED)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        logcat(INFO) { "SearchAndFavoritesWidget - onUpdate" }
        appCoroutineScope.launch(dispatchers.io()) {
            appWidgetIds.forEach { id ->
                updateWidget(context, appWidgetManager, id, null)
            }
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        logcat(INFO) { "SearchAndFavoritesWidget - onAppWidgetOptionsChanged" }
        appCoroutineScope.launch(dispatchers.io()) {
            updateWidget(context, appWidgetManager, appWidgetId, newOptions)
        }
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        appCoroutineScope.launch(dispatchers.io()) {
            appWidgetIds.forEach {
                widgetPrefs.removeWidgetSettings(it)
            }
        }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        searchWidgetLifecycleDelegate.handleOnWidgetDisabled(SEARCH_AND_FAVORITES_WIDGET_DELETED)
    }

    private suspend fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        val widgetTheme = withContext(dispatchers.io()) {
            widgetPrefs.widgetTheme(appWidgetId)
        }
        logcat(INFO) { "SearchAndFavoritesWidget theme for $appWidgetId is $widgetTheme" }

        val (columns, rows) = getCurrentWidgetSize(context, appWidgetManager.getAppWidgetOptions(appWidgetId), newOptions)
        layoutId = getLayoutThemed(columns, widgetTheme)

        withContext(dispatchers.io()) {
            widgetPrefs.storeWidgetSize(appWidgetId, columns, rows)
        }

        withContext(dispatchers.main()) {
            val remoteViews = RemoteViews(context.packageName, layoutId)

            remoteViews.setViewVisibility(R.id.searchInputBox, if (columns == 2) View.INVISIBLE else View.VISIBLE)
            remoteViews.setOnClickPendingIntent(R.id.widgetSearchBarContainer, buildPendingIntent(context))

            voiceSearchWidgetConfigurator.configureVoiceSearch(context, remoteViews, true)
            configureFavoritesGridView(context, appWidgetId, remoteViews, widgetTheme)
            configureEmptyWidgetCta(context, appWidgetId, remoteViews, widgetTheme)
// TODO: can this be moved to io?
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.favoritesGrid)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.emptyfavoritesGrid)
        }
    }

    private fun getLayoutThemed(
        numColumns: Int,
        theme: WidgetTheme,
    ): Int {
        // numcolumns method is not available for remoteViews. We rely on different xml to use different values on that attribute
        return when (theme) {
            WidgetTheme.LIGHT -> {
                when (numColumns) {
                    2 -> R.layout.search_favorites_widget_light_col2
                    3 -> R.layout.search_favorites_widget_light_col3
                    4 -> R.layout.search_favorites_widget_light_col4
                    5 -> R.layout.search_favorites_widget_light_col5
                    6 -> R.layout.search_favorites_widget_light_col6
                    else -> R.layout.search_favorites_widget_light_auto
                }
            }
            WidgetTheme.DARK -> {
                when (numColumns) {
                    2 -> R.layout.search_favorites_widget_dark_col2
                    3 -> R.layout.search_favorites_widget_dark_col3
                    4 -> R.layout.search_favorites_widget_dark_col4
                    5 -> R.layout.search_favorites_widget_dark_col5
                    6 -> R.layout.search_favorites_widget_dark_col6
                    else -> R.layout.search_favorites_widget_dark_auto
                }
            }
            WidgetTheme.SYSTEM_DEFAULT -> {
                when (numColumns) {
                    2 -> R.layout.search_favorites_widget_daynight_col2
                    3 -> R.layout.search_favorites_widget_daynight_col3
                    4 -> R.layout.search_favorites_widget_daynight_col4
                    5 -> R.layout.search_favorites_widget_daynight_col5
                    6 -> R.layout.search_favorites_widget_daynight_col6
                    else -> R.layout.search_favorites_widget_daynight_auto
                }
            }
        }
    }

    private fun getCurrentWidgetSize(
        context: Context,
        appWidgetOptions: Bundle,
        newOptions: Bundle?,
    ): Pair<Int, Int> {
        var portraitWidth = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        var landsWidth = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        var landsHeight = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        var portraitHeight = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)

        if (newOptions != null) {
            portraitWidth = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            landsWidth = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            landsHeight = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            portraitHeight = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        }

        val orientation = context.resources.configuration.orientation
        val width = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            landsWidth
        } else {
            portraitWidth
        }
        val height = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            landsHeight
        } else {
            portraitHeight
        }

        var columns = gridCalculator.calculateColumns(context, width)
        var rows = gridCalculator.calculateRows(context, height)

        logcat(INFO) { "SearchAndFavoritesWidget $portraitWidth x $portraitHeight -> $columns x $rows" }
        return Pair(columns, rows)
    }

    private fun configureFavoritesGridView(
        context: Context,
        appWidgetId: Int,
        remoteViews: RemoteViews,
        widgetTheme: WidgetTheme,
    ) {
        val favoriteItemClickIntent = Intent(context, BrowserActivity::class.java)
        val pendingIntentFlags = if (appBuildConfig.sdkInt >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val favoriteClickPendingIntent = PendingIntent.getActivity(context, 0, favoriteItemClickIntent, pendingIntentFlags)

        val extras = Bundle()
        extras.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        extras.putString(THEME_EXTRAS, widgetTheme.toString())

        val adapterIntent = Intent(context, FavoritesWidgetService::class.java)
        adapterIntent.putExtras(extras)
        adapterIntent.data = Uri.parse(adapterIntent.toUri(Intent.URI_INTENT_SCHEME))
        remoteViews.setRemoteAdapter(R.id.favoritesGrid, adapterIntent)
        remoteViews.setPendingIntentTemplate(R.id.favoritesGrid, favoriteClickPendingIntent)
    }

    private fun configureEmptyWidgetCta(
        context: Context,
        appWidgetId: Int,
        remoteViews: RemoteViews,
        widgetTheme: WidgetTheme,
    ) {
        val extras = Bundle()
        extras.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        extras.putString(THEME_EXTRAS, widgetTheme.toString())

        val emptyAdapterIntent = Intent(context, EmptyFavoritesWidgetService::class.java)
        emptyAdapterIntent.putExtras(extras)
        emptyAdapterIntent.data = Uri.parse(emptyAdapterIntent.toUri(Intent.URI_INTENT_SCHEME))
        remoteViews.setEmptyView(R.id.emptyfavoritesGrid, R.id.emptyGridViewContainer)
        remoteViews.setRemoteAdapter(R.id.emptyfavoritesGrid, emptyAdapterIntent)
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = SystemSearchActivity.fromFavWidget(context)
        return PendingIntent.getActivity(
            context,
            SEARCH_AND_FAVORITES_WIDGET_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun inject(context: Context) {
        val application = context.applicationContext as DuckDuckGoApplication
        application.daggerAppComponent.inject(this)
    }

    companion object {
        private const val SEARCH_AND_FAVORITES_WIDGET_REQUEST_CODE = 1540
    }
}
