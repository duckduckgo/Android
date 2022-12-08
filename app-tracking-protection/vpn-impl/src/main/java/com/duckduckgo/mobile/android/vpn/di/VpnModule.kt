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

package com.duckduckgo.mobile.android.vpn.di

import android.content.pm.PackageManager
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.*
import com.duckduckgo.mobile.android.vpn.store.*
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(VpnScope::class)
object VpnModule {

    @SingleInstanceIn(VpnScope::class)
    @Provides
    fun providesAppNameResolver(packageManager: PackageManager): AppNameResolver = RealAppNameResolver(packageManager)

    @Provides
    fun providesDispatcherProvider(): VpnDispatcherProvider {
        return DefaultVpnDispatcherProvider()
    }

    @Provides
    fun provideRuntime(): Runtime {
        return Runtime.getRuntime()
    }
}
