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

package com.duckduckgo.site.permissions.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.site.permissions.store.ALL_MIGRATIONS
import com.duckduckgo.site.permissions.store.SitePermissionsDatabase
import com.duckduckgo.site.permissions.store.SitePermissionsPreferences
import com.duckduckgo.site.permissions.store.SitePermissionsPreferencesImp
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsDao
import com.duckduckgo.site.permissions.store.sitepermissionsallowed.SitePermissionsAllowedDao
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(AppScope::class)
object SitePermissionsModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesSitePermissionsDatabase(context: Context): SitePermissionsDatabase {
        return Room.databaseBuilder(context, SitePermissionsDatabase::class.java, "site_permissions.db")
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesSitePermissionsDao(sitePermissionsDatabase: SitePermissionsDatabase): SitePermissionsDao {
        return sitePermissionsDatabase.sitePermissionsDao()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun providesSitePermissionsAllowedDao(sitePermissionsDatabase: SitePermissionsDatabase): SitePermissionsAllowedDao {
        return sitePermissionsDatabase.sitePermissionsAllowedDao()
    }

    @Provides
    fun providesSitePermissionsPreferences(context: Context): SitePermissionsPreferences {
        return SitePermissionsPreferencesImp(context)
    }
}
