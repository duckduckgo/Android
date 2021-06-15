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

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.duckduckgo.app.browser.R
import timber.log.Timber

class EmptyFavoritesWidgetService : RemoteViewsService() {

    companion object {
        const val MAX_ITEMS_EXTRAS = "MAX_ITEMS_EXTRAS"
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return FavoritesWidgetItemFactory(this.applicationContext, intent)
    }

    class FavoritesWidgetItemFactory(val context: Context, intent: Intent) : RemoteViewsFactory {

        private val maxItems = intent.extras?.getInt(MAX_ITEMS_EXTRAS, 2) ?: 2

        private val domains = mutableListOf<String>()

        override fun onCreate() {
        }

        override fun onDataSetChanged() {
        }

        override fun onDestroy() {
        }

        override fun getCount(): Int {
            Timber.i("SearchAndFavoritesWidget - getCount")
            return maxItems
        }

        override fun getViewAt(position: Int): RemoteViews {
            Timber.i("SearchAndFavoritesWidget - getViewAt")
            return RemoteViews(context.packageName, R.layout.view_favorite_widget_daynight_item)
        }

        override fun getLoadingView(): RemoteViews {
            return RemoteViews(context.packageName, R.layout.view_favorite_widget_daynight_item)
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
    }
}
