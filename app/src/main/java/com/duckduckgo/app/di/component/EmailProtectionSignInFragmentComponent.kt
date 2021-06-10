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

package com.duckduckgo.app.di.component

import com.duckduckgo.app.di.ActivityScoped
import com.duckduckgo.app.email.ui.EmailProtectionSignInFragment
import com.duckduckgo.di.scopes.AppObjectGraph

import com.duckduckgo.di.scopes.ActivityObjectGraph
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
interface EmailProtectionSignInFragmentComponent : AndroidInjector<EmailProtectionSignInFragment> {
    @Subcomponent.Factory
    interface Factory : AndroidInjector.Factory<EmailProtectionSignInFragment>
}

@ContributesTo(AppObjectGraph::class)
interface EmailProtectionSignInFragmentComponentProvider {
    fun provideEmailProtectionSignInFragmentComponentFactory(): EmailProtectionSignInFragmentComponent.Factory
}

@Module
@ContributesTo(AppObjectGraph::class)
abstract class EmailProtectionSignInFragmentBindingModule {
    @Binds
    @IntoMap
    @ClassKey(EmailProtectionSignInFragment::class)
    abstract fun bindEmailProtectionSignInFragmentComponentFactory(factory: EmailProtectionSignInFragmentComponent.Factory): AndroidInjector.Factory<*>
}
