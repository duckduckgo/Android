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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoApplication
import com.duckduckgo.app.systemsearch.SystemSearchActivity
import com.duckduckgo.widget.FavoritesWidgetService.Companion.MAX_ITEMS_EXTRAS
import com.duckduckgo.widget.FavoritesWidgetService.Companion.THEME_EXTRAS
import timber.log.Timber
import javax.inject.Inject


enum class WidgetTheme {
    LIGHT,
    DARK,
    SYSTEM_DEFAULT;

    companion object {
        fun getThemeFrom(value: String?): WidgetTheme {
            if (value.isNullOrEmpty()) return LIGHT
            return runCatching { valueOf(value) }.getOrDefault(LIGHT)
        }
    }
}

class SearchAndFavoritesWidget() : AppWidgetProvider() {

    companion object {
        const val ACTION_FAVORITE = "com.duckduckgo.widget.actionFavorite"
        const val EXTRA_ITEM_URL = "EXTRA_ITEM_URL"
    }

    @Inject
    lateinit var widgetPrefs: WidgetPreferences

    private var layoutId: Int = R.layout.search_favorites_widget_light_col3

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Timber.i("SearchAndFavoritesWidget - onUpdate")
        appWidgetIds.forEach { id ->
            updateWidget(context, appWidgetManager, id, null)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        Timber.i("SearchAndFavoritesWidget - onAppWidgetOptionsChanged")
        updateWidget(context, appWidgetManager, appWidgetId, newOptions)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        Timber.i("SearchAndFavoritesWidget - onDeleted")
        super.onDeleted(context, appWidgetIds)
    }

    override fun onEnabled(context: Context?) {
        Timber.i("SearchAndFavoritesWidget - onEnabled")
        super.onEnabled(context)
    }

    override fun onDisabled(context: Context?) {
        Timber.i("SearchAndFavoritesWidget - onDisabled")
        super.onDisabled(context)
    }

    override fun onRestored(context: Context?, oldWidgetIds: IntArray?, newWidgetIds: IntArray?) {
        Timber.i("SearchAndFavoritesWidget - onRestored")
        super.onRestored(context, oldWidgetIds, newWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        Timber.i("SearchAndFavoritesWidget - onReceive")
        inject(context)
        val instance = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, SearchAndFavoritesWidget::class.java)
        instance.notifyAppWidgetViewDataChanged(instance.getAppWidgetIds(componentName), R.id.favoritesGrid)

        super.onReceive(context, intent)
    }

    private fun inject(context: Context) {
        val application = context.applicationContext as DuckDuckGoApplication
        application.daggerAppComponent.inject(this)
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle?) {
        val widgetTheme = widgetPrefs.widgetTheme(appWidgetId)
        Timber.i("SearchAndFavoritesWidget theme for $appWidgetId is $widgetTheme")

        val appWidgetOptions = appWidgetManager.getAppWidgetOptions(appWidgetId)
        var minWidth = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) // portrait
        var maxWidth = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) // landscape
        var minHeight = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) // landscape
        var maxHeight = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) // portrait

        if (newOptions != null) {
            minWidth = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) // portrait
            maxWidth = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH) // landscape
            minHeight = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) // landscape
            maxHeight = appWidgetOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) // portrait
        }

        // pixel 2 -> 2x2 -> 214 x 176
        // nexus 5 -> 2x2 -> 156 x 204

        val (columns, rows) = getCurrentWidgetSize(minWidth, maxWidth, minHeight, maxHeight)
        Timber.i("SearchAndFavoritesWidget $minWidth x $maxHeight -> $columns x $rows")

        layoutId = getLayoutThemed(columns, widgetTheme)

        val remoteViews = RemoteViews(context.packageName, layoutId)

        val favoriteItemClickIntent = Intent(context, BrowserActivity::class.java)
        val favoriteClickPendingIntent = PendingIntent.getActivity(context, 0, favoriteItemClickIntent, 0)

        remoteViews.setOnClickPendingIntent(R.id.widgetSearchBarContainer, buildPendingIntent(context))

        val extras = Bundle()
        extras.putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        extras.putString(THEME_EXTRAS, widgetTheme.toString())
        extras.putInt(MAX_ITEMS_EXTRAS, columns * rows)

        val adapterIntent = Intent(context, FavoritesWidgetService::class.java)
        adapterIntent.putExtras(extras)
        adapterIntent.data = Uri.parse(adapterIntent.toUri(Intent.URI_INTENT_SCHEME))
        remoteViews.setRemoteAdapter(R.id.favoritesGrid, adapterIntent)
        remoteViews.setEmptyView(R.id.favoritesGrid, R.id.emptyGridViewContainer)
        remoteViews.setPendingIntentTemplate(R.id.favoritesGrid, favoriteClickPendingIntent)

        val emptyAdapterIntent = Intent(context, EmptyFavoritesWidgetService::class.java)
        emptyAdapterIntent.putExtras(extras)
        emptyAdapterIntent.data = Uri.parse(emptyAdapterIntent.toUri(Intent.URI_INTENT_SCHEME))
        remoteViews.setRemoteAdapter(R.id.emptyfavoritesGrid, emptyAdapterIntent)

        appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.favoritesGrid)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.emptyfavoritesGrid)
    }

    private fun getLayoutThemed(numColumns: Int, theme: WidgetTheme): Int {
        return when(theme) {
           WidgetTheme.LIGHT -> {
               when (numColumns) {
                   2 -> R.layout.search_favorites_widget_light_col2
                   3 -> R.layout.search_favorites_widget_light_col3
                   4 -> R.layout.search_favorites_widget_light_col4
                   else -> R.layout.search_favorites_widget_light_col2
               }
           }
           WidgetTheme.DARK -> {
               when (numColumns) {
                   2 -> R.layout.search_favorites_widget_dark_col2
                   3 -> R.layout.search_favorites_widget_dark_col3
                   4 -> R.layout.search_favorites_widget_dark_col4
                   else -> R.layout.search_favorites_widget_dark_col4
               }
           }
           WidgetTheme.SYSTEM_DEFAULT -> {
               when (numColumns) {
                   2 -> R.layout.search_favorites_widget_daynight_col2
                   3 -> R.layout.search_favorites_widget_daynight_col3
                   4 -> R.layout.search_favorites_widget_daynight_col4
                   else -> R.layout.search_favorites_widget_daynight_col2
               }
           }
        }
    }

    private fun getCurrentWidgetSize(minWidth: Int, maxWidth: Int, minHeight: Int, maxHeight: Int): Pair<Int, Int> {
        var columns = calculateColumns(minWidth)
        var rows = calculateRows(maxHeight)

        columns = if (columns < 2) 2 else columns
        columns = if (columns > 4) 4 else columns

        return Pair(columns, rows)
    }

    private fun calculateColumns(width: Int): Int {
        val margins = 8
        val item = 64
        val divider = 4
        var n = 2
        var totalSize = (n * item) + ((n - 1) * divider) + margins

        Timber.i("SearchAndFavoritesWidget width n:$n $totalSize vs $width")

        while (totalSize < width) {
            ++n
            totalSize = (n * item) + ((n - 1) * divider) + margins
            Timber.i("SearchAndFavoritesWidget width n:$n $totalSize vs $width")
        }
        return n - 1
    }

    private fun calculateRows(height: Int): Int {
        val searchBar = 44 + 24
        val margins = 32
        val item = 78
        val divider = 24
        var n = 1
        var totalSize = searchBar + (n * item) + ((n - 1) * divider) + margins

        Timber.i("SearchAndFavoritesWidget height n:$n $totalSize vs $height")

        while (totalSize < height) {
            ++n
            totalSize = searchBar + (n * item) + ((n - 1) * divider) + margins
            Timber.i("SearchAndFavoritesWidget height n:$n $totalSize vs $height")
        }

        return n - 1
    }

    private fun getCurrentWidgetSizeBasedOnGuidelines(minWidth: Int, maxWidth: Int, minHeight: Int, maxHeight: Int): Pair<Int, Int> {
        val rows = ((maxHeight + 30) / 70)
        val columns = ((minWidth + 30) / 70)

        return Pair(rows, columns)
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = SystemSearchActivity.fromWidget(context)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
