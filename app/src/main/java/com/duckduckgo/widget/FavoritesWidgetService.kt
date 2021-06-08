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
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.duckduckgo.app.bookmarks.model.FavoritesDataRepository
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoApplication
import com.duckduckgo.app.systemsearch.SystemSearchActivity
import timber.log.Timber
import javax.inject.Inject

class FavoritesWidgetService: RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return FavoritesWidgetItemFactory(this.applicationContext, intent)
    }

    class FavoritesWidgetItemFactory(val context: Context, intent: Intent): RemoteViewsFactory {

        @Inject
        lateinit var favoritesDataRepository: FavoritesRepository

        private val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID)

        private val domains = mutableListOf<String>()

        override fun onCreate() {
            inject(context)
        }

        override fun onDataSetChanged() {
            domains.clear()
            domains.addAll(favoritesDataRepository.favoritesBlockingGet().map { it.title })
        }

        override fun onDestroy() {
        }

        override fun getCount(): Int {
            Timber.i("SearchAndFavoritesWidget - getCount")
            return domains.size
        }

        override fun getViewAt(position: Int): RemoteViews {
            Timber.i("SearchAndFavoritesWidget - getViewAt")
            val item = domains[position]
            val remoteViews = RemoteViews(context.packageName, R.layout.view_favorite_widget_item)
            remoteViews.setTextViewText(R.id.quickAccessTitle, item)
            configureClickListener(remoteViews, item)
            return remoteViews
        }

        private fun configureClickListener(remoteViews: RemoteViews, item: String) {
            val bundle = Bundle()
            bundle.putString(Intent.EXTRA_TEXT, item)
            bundle.putBoolean(BrowserActivity.NEW_SEARCH_EXTRA, false)
            bundle.putBoolean(BrowserActivity.NOTIFY_DATA_CLEARED_EXTRA, false)
            val intent = Intent()
            intent.putExtras(bundle)
            remoteViews.setOnClickFillInIntent(R.id.quickAccessFaviconContainer, intent)
        }

        override fun getLoadingView(): RemoteViews {
            return RemoteViews(context.packageName, R.layout.view_favorite_widget_item)
        }

        override fun getViewTypeCount(): Int {
            return 1
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun hasStableIds(): Boolean {
            return true
        }

        private fun buildPendingIntent(context: Context): PendingIntent {
            val intent = SystemSearchActivity.fromWidget(context)
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun inject(context: Context) {
            val application = context.applicationContext as DuckDuckGoApplication
            application.daggerAppComponent.inject(this)
        }
    }
}