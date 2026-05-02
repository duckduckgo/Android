/*
 * Copyright (c) 2017 DuckDuckGo
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

import android.app.Application
import com.duckduckgo.app.global.DuckDuckGoApplication
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.di.scopes.ReceiverScope
import com.duckduckgo.di.scopes.ServiceScope
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.widget.EmptyFavoritesWidgetItemFactory
import com.duckduckgo.widget.FavoritesWidgetItemFactory
import com.duckduckgo.widget.SearchAndFavoritesWidget
import com.duckduckgo.widget.SearchOnlyWidget
import com.duckduckgo.widget.SearchWidget
import dagger.android.AndroidInjector
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import retrofit2.Retrofit
import javax.inject.Named

@SingleIn(AppScope::class)
@DependencyGraph(scope = AppScope::class)
interface AppComponent : AndroidInjector<DuckDuckGoApplication> {

    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Provides application: Application,
            @Provides @AppCoroutineScope applicationCoroutineScope: CoroutineScope,
        ): AppComponent
    }

    fun inject(searchWidget: SearchWidget)

    fun inject(searchOnlyWidget: SearchOnlyWidget)

    fun inject(searchAndFavoritesWidget: SearchAndFavoritesWidget)

    fun inject(favoritesWidgetItemFactory: FavoritesWidgetItemFactory)

    fun inject(emptyFavoritesWidgetItemFactory: EmptyFavoritesWidgetItemFactory)

    // accessor to Retrofit instance for test only
    @Named("api")
    fun retrofit(): Retrofit
}
