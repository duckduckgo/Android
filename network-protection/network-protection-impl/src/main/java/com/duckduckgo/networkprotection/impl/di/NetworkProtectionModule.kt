/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import com.duckduckgo.mobile.android.vpn.ui.AppBreakageCategory
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistDataStoreSharedPreferences
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.duckduckgo.networkprotection.impl.waitlist.store.RealNetPWaitlistRepository
import com.duckduckgo.networkprotection.store.NetPExclusionListRepository
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.store.RealNetPExclusionListRepository
import com.duckduckgo.networkprotection.store.RealNetworkProtectionPrefs
import com.duckduckgo.networkprotection.store.RealNetworkProtectionRepository
import com.duckduckgo.networkprotection.store.db.NetPDatabase
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(AppScope::class)
class DataModule {
    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideNetworkProtectionRepository(
        vpnSharedPreferencesProvider: VpnSharedPreferencesProvider,
    ): NetworkProtectionRepository = RealNetworkProtectionRepository(RealNetworkProtectionPrefs(vpnSharedPreferencesProvider))

    @Provides
    fun provideNetPWaitlistRepository(
        vpnSharedPreferencesProvider: VpnSharedPreferencesProvider,
        dispatcherProvider: DispatcherProvider,
    ): NetPWaitlistRepository = RealNetPWaitlistRepository(NetPWaitlistDataStoreSharedPreferences(vpnSharedPreferencesProvider), dispatcherProvider)

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun bindNetPDatabase(context: Context): NetPDatabase {
        return Room.databaseBuilder(context, NetPDatabase::class.java, "vpn-netp.db")
            .enableMultiInstanceInvalidation()
            .fallbackToDestructiveMigration()
            .addMigrations(*NetPDatabase.ALL_MIGRATIONS.toTypedArray())
            .build()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideAppTrackerLoader(
        database: NetPDatabase,
    ): NetPExclusionListRepository {
        return RealNetPExclusionListRepository(database.exclusionListDao())
    }
}

@Module
@ContributesTo(ActivityScope::class)
object NetPBreakageCategoriesModule {
    @Provides
    @NetpBreakageCategories
    fun provideAppTrackerBreakageCategories(context: Context): List<AppBreakageCategory> {
        return mutableListOf(
            AppBreakageCategory("crashes", context.getString(R.string.netpReportBreakageCategoryCrashes)),
            AppBreakageCategory("messages", context.getString(R.string.netpReportBreakageCategoryMessages)),
            AppBreakageCategory("calls", context.getString(R.string.netpReportBreakageCategoryCalls)),
            AppBreakageCategory("content", context.getString(R.string.netpReportBreakageCategoryContent)),
            AppBreakageCategory("connection", context.getString(R.string.netpReportBreakageCategoryConnection)),
            AppBreakageCategory("iot", context.getString(R.string.netpReportBreakageCategoryIot)),
        ).apply {
            shuffle()
            add(AppBreakageCategory("featurerequest", context.getString(R.string.netpReportBreakageCategoryFeatureRequest)))
            add(AppBreakageCategory("other", context.getString(R.string.netpReportBreakageCategoryOther)))
        }
    }
}
