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
import com.duckduckgo.di.android.AndroidInjector
import com.duckduckgo.di.android.InjectableActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides

@GraphExtension(scope = ActivityScope::class)
interface ActivityComponent : AndroidInjector<InjectableActivity> {

    @GraphExtension.Factory
    interface Factory : AndroidInjector.Factory<InjectableActivity, ActivityComponent> {
        override fun create(@Provides instance: InjectableActivity): ActivityComponent
    }

    @ContributesTo(AppScope::class)
    interface ParentComponent {
        fun activityComponentFactory(): Factory
    }
}

@BindingContainer
@ContributesTo(AppScope::class)
abstract class InjectableActivityModule {
    @Binds
    @IntoMap
    @ClassKey(InjectableActivity::class)
    abstract fun bindsInjectableActivityComponent(factory: Factory): AndroidInjector.Factory<*, *>
}

@BindingContainer
@ContributesTo(ActivityScope::class)
abstract class InjectableActivityScopedModule {
    @Binds
    abstract fun bindsInjectableActivity(activity: InjectableActivity): AppCompatActivity

    @Binds
    @ActivityContext
    abstract fun bindsActivityContext(activity: InjectableActivity): Context
}
