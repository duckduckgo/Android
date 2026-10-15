/*
 * Copyright (c) 2024 DuckDuckGo
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
import androidx.appcompat.app.AppCompatActivity
import com.duckduckgo.app.di.ActivityComponent.Factory
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import dagger.android.AndroidInjector
import dagger.android.DaggerActivity
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides

@GraphExtension(scope = ActivityScope::class)
interface ActivityComponent : AndroidInjector<DaggerActivity> {

    @GraphExtension.Factory
    interface Factory : AndroidInjector.Factory<DaggerActivity, ActivityComponent> {
        override fun create(@Provides instance: DaggerActivity): ActivityComponent
    }

    @ContributesTo(AppScope::class)
    interface ParentComponent {
        fun activityComponentFactory(): Factory
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
abstract class DaggerActivityModule {
    @Binds
    @IntoMap
    @ClassKey(DaggerActivity::class)
    abstract fun bindsDaggerActivityComponent(factory: Factory): AndroidInjector.Factory<*, *>
}

@BindingContainer
@ContributesTo(ActivityScope::class)
abstract class DaggerActivityScopedModule {
    @Binds
    abstract fun bindsDaggerActivity(daggerActivity: DaggerActivity): AppCompatActivity

    @Binds
    @ActivityContext
    abstract fun bindsActivityContext(daggerActivity: DaggerActivity): Context
}
