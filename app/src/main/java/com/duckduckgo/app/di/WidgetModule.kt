/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.di

import android.content.Context
import com.duckduckgo.app.widget.ui.AppWidgetCapabilities
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.widget.SearchAndFavoritesGridCalculator
import dagger.Module
import dagger.Provides
import dagger.SingleIn

@Module
class WidgetModule {

    @Provides
    @SingleIn(AppObjectGraph::class)
    fun widgetCapabilities(context: Context): WidgetCapabilities = AppWidgetCapabilities(context)

    @Provides
    fun gridCalculator(): SearchAndFavoritesGridCalculator = SearchAndFavoritesGridCalculator()
}
