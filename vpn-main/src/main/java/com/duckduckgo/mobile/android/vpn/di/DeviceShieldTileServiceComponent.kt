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

package com.duckduckgo.mobile.android.vpn.di

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.QuickSettingsScope
import com.duckduckgo.mobile.android.vpn.service.DeviceShieldTileService
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeSubcomponent
import dagger.Binds
import dagger.Module
import dagger.SingleInstanceIn
import dagger.Subcomponent
import dagger.android.AndroidInjector
import dagger.binding.TileServiceBingingKey
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

@SingleInstanceIn(QuickSettingsScope::class)
@MergeSubcomponent(scope = QuickSettingsScope::class)
interface DeviceShieldTileServiceComponent : AndroidInjector<DeviceShieldTileService> {
    @Subcomponent.Factory interface Factory : AndroidInjector.Factory<DeviceShieldTileService>
}

@ContributesTo(AppScope::class)
interface DeviceShieldTileServiceComponentProvider {
    fun provideDeviceShieldTileServiceComponentFactory(): DeviceShieldTileServiceComponent.Factory
}

@Module
@ContributesTo(AppScope::class)
abstract class DeviceShieldTileServiceBindingModule {
    @Binds
    @IntoMap
    // We don't use the DeviceShieldTileService::class as binding key because TileService (Android)
    // class does not
    // exist in all APIs, and so using it DeviceShieldTileService::class as key would compile but
    // immediately crash
    // at startup when Java class loader tries to resolve the TileService::class upon Dagger setup
    @ClassKey(TileServiceBingingKey::class)
    abstract fun DeviceShieldTileServiceComponent.Factory.bind(): AndroidInjector.Factory<*>
}
