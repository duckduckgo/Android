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

import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.di.scopes.VpnObjectGraph
import com.duckduckgo.mobile.android.vpn.di.VpnScope

import com.duckduckgo.vpn.internal.feature.VpnInternalSettingsActivity
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeSubcomponent
import dagger.Binds
import dagger.Module
import dagger.Subcomponent
import dagger.android.AndroidInjector
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

@VpnScope
@MergeSubcomponent(
    scope = VpnObjectGraph::class
)
interface VpnInternalSettingsActivityComponent : AndroidInjector<VpnInternalSettingsActivity> {
    @Subcomponent.Factory
    interface Factory : AndroidInjector.Factory<VpnInternalSettingsActivity>
}

@ContributesTo(AppObjectGraph::class)
interface VpnInternalSettingsActivityComponentProvider {
    fun provideVpnInternalSettingsActivityComponentFactory(): VpnInternalSettingsActivityComponent.Factory
}

@Module
@ContributesTo(AppObjectGraph::class)
abstract class VpnInternalSettingsActivityBindingModule {
    @Binds
    @IntoMap
    @ClassKey(VpnInternalSettingsActivity::class)
    abstract fun bindVpnInternalSettingsActivityComponentFactory(factory: VpnInternalSettingsActivityComponent.Factory): AndroidInjector.Factory<*>
}
