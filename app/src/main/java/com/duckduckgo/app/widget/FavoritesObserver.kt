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

package com.duckduckgo.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.coroutineScope
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.widget.SearchAndFavoritesWidget
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.launch

@SingleInstanceIn(AppScope::class)
class FavoritesObserver @Inject constructor(
    context: Context,
    private val savedSitesRepository: SavedSitesRepository,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {

    private val appWidgetManager: AppWidgetManager? by lazy {
        AppWidgetManager.getInstance(context)
    }
    private val componentName = ComponentName(context, SearchAndFavoritesWidget::class.java)

    override fun onStart(owner: LifecycleOwner) {
        owner.lifecycle.coroutineScope.launch(dispatcherProvider.io()) {
            appWidgetManager?.let { instance ->
                savedSitesRepository.getFavorites().collect {
                    instance.notifyAppWidgetViewDataChanged(instance.getAppWidgetIds(componentName), R.id.favoritesGrid)
                    instance.notifyAppWidgetViewDataChanged(instance.getAppWidgetIds(componentName), R.id.emptyfavoritesGrid)
                }
            }
        }
    }
}
