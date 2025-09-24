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
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoApplication
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.savedsites.api.SavedSitesRepository
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.logcat

/**
 * This RemoteViewsFactory will not render any item. It's used for convenience to simplify executing background operations to show/hide empty widget CTA.
 * If this RemoteViewsFactory count is 0, SearchAndFavoritesWidget R.id.emptyfavoritesGrid will show the configured EmptyView.
 */
class EmptyFavoritesWidgetItemFactory(
    val context: Context,
) : RemoteViewsService.RemoteViewsFactory {

    @Inject
    lateinit var savedSitesRepository: SavedSitesRepository

    @Inject
    lateinit var dispatchers: DispatcherProvider

    private var count = 0

    override fun onCreate() {
        inject(context)
    }

    override fun onDataSetChanged() {
        // no-op, we use our own update mechanism
    }

    suspend fun updateEmptyWidgetFavoritesAsync() {
        runCatching {
            count = getItemsCountFromFavorites()
        }.onFailure { error ->
            logcat { "Failed to update Search and Favorites widget when empty: ${error.message}" }
        }
    }

    override fun onDestroy() {
        // no-op
    }

    override fun getCount(): Int {
        return count
    }

    override fun getViewAt(position: Int): RemoteViews {
        return RemoteViews(context.packageName, R.layout.empty_view)
    }

    override fun getLoadingView(): RemoteViews {
        return RemoteViews(context.packageName, R.layout.empty_view)
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

    private suspend fun getItemsCountFromFavorites(): Int {
        return withContext(dispatchers.io()) {
            if (savedSitesRepository.hasFavorites()) 1 else 0
        }
    }

    private fun inject(context: Context) {
        val application = context.applicationContext as DuckDuckGoApplication
        application.daggerAppComponent.inject(this)
    }
}
