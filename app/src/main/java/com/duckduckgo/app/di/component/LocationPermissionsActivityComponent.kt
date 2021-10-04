/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.di.component

import com.duckduckgo.app.di.ActivityScoped
import com.duckduckgo.app.location.ui.LocationPermissionsActivity
import com.duckduckgo.di.scopes.ActivityObjectGraph
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeSubcomponent
import dagger.Binds
import dagger.Module
import dagger.Subcomponent
import dagger.android.AndroidInjector
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

@ActivityScoped
@MergeSubcomponent(
    scope = ActivityObjectGraph::class
)
interface LocationPermissionsActivityComponent : AndroidInjector<LocationPermissionsActivity> {
    @Subcomponent.Factory
    interface Factory : AndroidInjector.Factory<LocationPermissionsActivity>
}

@ContributesTo(AppObjectGraph::class)
interface LocationPermissionsActivityComponentProvider {
    fun provideLocationPermissionsActivityComponentFactory(): LocationPermissionsActivityComponent.Factory
}

@Module
@ContributesTo(AppObjectGraph::class)
abstract class LocationPermissionsActivityBindingModule {
    @Binds
    @IntoMap
    @ClassKey(LocationPermissionsActivity::class)
    abstract fun bindLocationPermissionsActivityComponentFactory(factory: LocationPermissionsActivityComponent.Factory): AndroidInjector.Factory<*>
}
