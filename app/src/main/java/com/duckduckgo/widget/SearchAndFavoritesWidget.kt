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
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.core.widget.RemoteViewsCompat
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DuckDuckGoApplication
import com.duckduckgo.app.pixels.AppPixelName.SEARCH_AND_FAVORITES_WIDGET_ADDED
import com.duckduckgo.app.pixels.AppPixelName.SEARCH_AND_FAVORITES_WIDGET_DELETED
import com.duckduckgo.app.systemsearch.SystemSearchActivity
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.widget.FavoritesWidgetItemFactory.Companion.THEME_EXTRAS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.LogPriority.INFO
import logcat.logcat
import javax.inject.Inject

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
    lateinit var searchWidgetConfigurator: SearchWidgetConfigurator

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
        logcat(INFO) { "SearchAndFavoritesWidget: onUpdate" }
        // need to use goAsync since updating the widget may take some time
        // and without it onUpdate/onAppWidgetOptionsChanged could be called multiple times at same time
        goSafeAsync {
            appWidgetIds.forEach { id ->
                updateWidget(context, appWidgetManager, id, null)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        logcat(INFO) { "SearchAndFavoritesWidget: onAppWidgetOptionsChanged" }
        // need to use goAsync since updating the widget may take some time
        // and without it onAppWidgetOptionsChanged/onUpdate could be called multiple times at same time
        goSafeAsync {
            updateWidget(context, appWidgetManager, appWidgetId, newOptions)
        }
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
        layoutId = getLayoutThemed(widgetTheme)

        withContext(dispatchers.io()) {
            widgetPrefs.storeWidgetSize(appWidgetId, columns, rows)
        }

        withContext(dispatchers.main()) {
            val remoteViews = RemoteViews(context.packageName, layoutId)

            remoteViews.setOnClickPendingIntent(R.id.widgetSearchBarContainer, buildPendingIntent(context))

            searchWidgetConfigurator.populateRemoteViews(
                context = context,
                remoteViews = remoteViews,
                fromFavWidget = true,
            )
            configureFavoritesGridView(context, appWidgetId, remoteViews, widgetTheme)
            configureEmptyWidgetCta(context, appWidgetId, remoteViews)

            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }

    private fun getLayoutThemed(
        theme: WidgetTheme,
    ): Int {
        return when (theme) {
            WidgetTheme.LIGHT -> R.layout.search_favorites_widget_light_auto
            WidgetTheme.DARK -> R.layout.search_favorites_widget_dark_auto
            WidgetTheme.SYSTEM_DEFAULT -> R.layout.search_favorites_widget_daynight_auto
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
            portraitWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            landsWidth = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            landsHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            portraitHeight = newOptions.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
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

        val columns = gridCalculator.calculateColumns(context, width)
        val rows = gridCalculator.calculateRows(context, height)

        logcat(INFO) { "SearchAndFavoritesWidget $portraitWidth x $portraitHeight -> $columns x $rows" }
        return Pair(columns, rows)
    }

    private suspend fun configureFavoritesGridView(
        context: Context,
        appWidgetId: Int,
        remoteViews: RemoteViews,
        widgetTheme: WidgetTheme,
    ) {
        val favoriteItemClickIntent = Intent(context, BrowserActivity::class.java)
        val pendingIntentFlags = if (appBuildConfig.sdkInt >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val favoriteClickPendingIntent = PendingIntent.getActivity(context, 0, favoriteItemClickIntent, pendingIntentFlags)

        val items = buildRemoteCollectionItems(context, appWidgetId, widgetTheme)
        RemoteViewsCompat.setRemoteAdapter(context, remoteViews, appWidgetId, R.id.favoritesGrid, items)
        remoteViews.setPendingIntentTemplate(R.id.favoritesGrid, favoriteClickPendingIntent)
    }

    private suspend fun configureEmptyWidgetCta(
        context: Context,
        appWidgetId: Int,
        remoteViews: RemoteViews,
    ) {
        val items = buildRemoteEmptyCollectionItems(context)
        remoteViews.setEmptyView(R.id.emptyFavoritesGrid, R.id.emptyGridViewContainer)
        RemoteViewsCompat.setRemoteAdapter(context, remoteViews, appWidgetId, R.id.emptyFavoritesGrid, items)
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

    private suspend fun buildRemoteCollectionItems(
        context: Context,
        appWidgetId: Int,
        widgetTheme: WidgetTheme,
    ): RemoteViewsCompat.RemoteCollectionItems {
        val factory = FavoritesWidgetItemFactory(
            context,
            Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(THEME_EXTRAS, widgetTheme.toString())
            },
        )
        factory.onCreate()
        factory.updateWidgetFavoritesAsync()

        val builder = RemoteViewsCompat.RemoteCollectionItems.Builder()
        val count = factory.count
        for (i in 0 until count) {
            val itemId = factory.getItemId(i)
            val remoteView = factory.getViewAt(i)
            builder.addItem(itemId, remoteView)
        }
        factory.onDestroy()
        return builder.build()
    }

    private suspend fun buildRemoteEmptyCollectionItems(
        context: Context,
    ): RemoteViewsCompat.RemoteCollectionItems {
        val factory = EmptyFavoritesWidgetItemFactory(
            context,
        )
        factory.onCreate()
        factory.updateEmptyWidgetFavoritesAsync()

        val builder = RemoteViewsCompat.RemoteCollectionItems.Builder()
        val count = factory.count
        for (i in 0 until count) {
            val itemId = factory.getItemId(i)
            val remoteView = factory.getViewAt(i)
            builder.addItem(itemId, remoteView)
        }
        factory.onDestroy()
        return builder.build()
    }

    @Suppress("NoHardcodedCoroutineDispatcher")
    private fun goSafeAsync(
        block: suspend () -> Unit,
    ) {
        if (Build.MANUFACTURER.equals("vivo", ignoreCase = true)) {
            // avoid calling goAsync() on Vivo devices as calling finish() on the PendingResult can cause crash:
            // https://issuetracker.google.com/issues/257513022
            appCoroutineScope.launch {
                try {
                    block()
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Failed to finish PendingResult: $e" }
                }
            }
            return
        }

        val pendingResult = goAsync()
        appCoroutineScope.launch {
            try {
                block()
            } finally {
                // Always call finish(), even if the coroutineScope was cancelled
                try {
                    pendingResult?.finish()
                } catch (e: Exception) {
                    // on some devices like Vivo, calling finish() can throw an exception: https://issuetracker.google.com/issues/257513022
                    logcat(LogPriority.ERROR) { "Failed to finish PendingResult: $e" }
                }
            }
        }
    }

    companion object {
        private const val SEARCH_AND_FAVORITES_WIDGET_REQUEST_CODE = 1540
    }
}
