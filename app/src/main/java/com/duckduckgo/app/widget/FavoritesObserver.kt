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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.browser.R
import com.duckduckgo.widget.SearchAndFavoritesWidget
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesObserver @Inject constructor(
    private val context: Context,
    private val favoritesRepository: FavoritesRepository,
    private val appCoroutineScope: CoroutineScope
) : LifecycleObserver {

    private val instance = AppWidgetManager.getInstance(context)
    private val componentName = ComponentName(context, SearchAndFavoritesWidget::class.java)

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun notifyWidgets() {
        appCoroutineScope.launch() {
            favoritesRepository.favorites().collect {
                instance.notifyAppWidgetViewDataChanged(instance.getAppWidgetIds(componentName), R.id.favoritesGrid)
                instance.notifyAppWidgetViewDataChanged(instance.getAppWidgetIds(componentName), R.id.emptyfavoritesGrid)
            }
        }
    }
}
