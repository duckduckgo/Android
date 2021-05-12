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

import android.content.Context
import android.content.res.Resources
import android.net.ConnectivityManager
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerRepository
import com.duckduckgo.mobile.android.vpn.trackers.RealAppTrackerRepository
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
@ContributesTo(AppObjectGraph::class)
class VpnAppModule {

    @Singleton
    @Provides
    fun providesConnectivityManager(context: Context): ConnectivityManager {
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    /**
     * TODO this class should also not be needed in the AppScope.
     * It is needed because the DaggerWorkerFactory is not modular. Easy to fix tho
     */
    @Singleton
    @Provides
    fun bindVpnDatabase(context: Context): VpnDatabase {
        return VpnDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideAppTrackerLoader(context: Context, moshi: Moshi, vpnDatabase: VpnDatabase): AppTrackerRepository {
        return RealAppTrackerRepository(vpnDatabase.vpnAppTrackerBlockingDao())
    }

    @Provides
    @Singleton
    fun providesResources(context: Context): Resources {
        return context.resources
    }
}
