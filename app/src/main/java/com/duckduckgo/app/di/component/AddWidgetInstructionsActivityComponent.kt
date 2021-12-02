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

import com.duckduckgo.app.widget.ui.AddWidgetInstructionsActivity
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ActivityScope
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeSubcomponent
import dagger.Binds
import dagger.Module
import dagger.SingleInstanceIn
import dagger.Subcomponent
import dagger.android.AndroidInjector
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

@SingleInstanceIn(ActivityScope::class)
@MergeSubcomponent(
    scope = ActivityScope::class
)
interface AddWidgetInstructionsActivityComponent : AndroidInjector<AddWidgetInstructionsActivity> {
    @Subcomponent.Factory
    interface Factory : AndroidInjector.Factory<AddWidgetInstructionsActivity>
}

@ContributesTo(AppScope::class)
interface AddWidgetInstructionsActivityComponentProvider {
    fun provideAddWidgetInstructionsActivityComponentFactory(): AddWidgetInstructionsActivityComponent.Factory
}

@Module
@ContributesTo(AppScope::class)
abstract class AddWidgetInstructionsActivityBindingModule {
    @Binds
    @IntoMap
    @ClassKey(AddWidgetInstructionsActivity::class)
    abstract fun AddWidgetInstructionsActivityComponent.Factory.bind(): AndroidInjector.Factory<*>
}
