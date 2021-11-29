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

import com.duckduckgo.app.privacy.ui.PrivacyDashboardActivity
import com.duckduckgo.di.scopes.ActivityObjectGraph
import com.duckduckgo.di.scopes.AppObjectGraph
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeSubcomponent
import dagger.Binds
import dagger.Module
import dagger.SingleIn
import dagger.Subcomponent
import dagger.android.AndroidInjector
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

@SingleIn(ActivityObjectGraph::class)
@MergeSubcomponent(
    scope = ActivityObjectGraph::class
)
interface PrivacyDashboardActivityComponent : AndroidInjector<PrivacyDashboardActivity> {
    @Subcomponent.Factory
    interface Factory : AndroidInjector.Factory<PrivacyDashboardActivity>
}

@ContributesTo(AppObjectGraph::class)
interface PrivacyDashboardActivityComponentProvider {
    fun providePrivacyDashboardActivityComponentFactory(): PrivacyDashboardActivityComponent.Factory
}

@Module
@ContributesTo(AppObjectGraph::class)
abstract class PrivacyDashboardActivityBindingModule {
    @Binds
    @IntoMap
    @ClassKey(PrivacyDashboardActivity::class)
    abstract fun PrivacyDashboardActivityComponent.Factory.bind(): AndroidInjector.Factory<*>
}
