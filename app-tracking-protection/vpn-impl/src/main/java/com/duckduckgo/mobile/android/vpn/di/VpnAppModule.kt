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
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.stats.AppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.stats.RealAppTrackerBlockingStatsRepository
import com.duckduckgo.mobile.android.vpn.store.*
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerRepository
import com.duckduckgo.mobile.android.vpn.trackers.RealAppTrackerRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import javax.inject.Provider
import javax.inject.Qualifier

@Module
@ContributesTo(AppScope::class)
object VpnAppModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun providesConnectivityManager(context: Context): ConnectivityManager {
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @Provides
    fun provideVpnDatabaseCallbackProvider(
        context: Context,
        vpnDatabase: Provider<VpnDatabase>,
        dispatcherProvider: DispatcherProvider,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        @AppTpBlocklistUpdateMutex mutex: Mutex,
    ): VpnDatabaseCallbackProvider {
        return VpnDatabaseCallbackProvider(context, vpnDatabase, dispatcherProvider, coroutineScope, mutex)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    @AppTpBlocklistUpdateMutex
    fun providesAppTpBlocklistUpdateMutex(): Mutex {
        return Mutex()
    }

    /**
     * TODO this class should also not be needed in the AppScope.
     * It is needed because the DaggerWorkerFactory is not modular. Easy to fix tho
     */
    @SingleInstanceIn(AppScope::class)
    @Provides
    fun bindVpnDatabase(
        context: Context,
        vpnDatabaseCallbackProvider: VpnDatabaseCallbackProvider,
    ): VpnDatabase {
        return Room.databaseBuilder(context, VpnDatabase::class.java, "vpn.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigrationFrom(*IntRange(1, 17).toList().toIntArray())
            .addMigrations(*VpnDatabase.ALL_MIGRATIONS.toTypedArray())
            .addCallback(vpnDatabaseCallbackProvider.provideCallbacks())
            .build()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideAppTrackerLoader(
        vpnDatabase: VpnDatabase,
    ): AppTrackerRepository {
        return RealAppTrackerRepository(vpnDatabase.vpnAppTrackerBlockingDao(), vpnDatabase.vpnSystemAppsOverridesDao())
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesResources(context: Context): Resources {
        return context.resources
    }

    @Provides
    fun provideAppTrackerBlockingStatsRepository(
        vpnDatabase: VpnDatabase,
        dispatchers: DispatcherProvider,
    ): AppTrackerBlockingStatsRepository {
        return RealAppTrackerBlockingStatsRepository(vpnDatabase, dispatchers)
    }
}

@Retention(AnnotationRetention.BINARY)
@Qualifier
internal annotation class AppTpBlocklistUpdateMutex
