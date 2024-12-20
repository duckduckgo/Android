/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.malicioussiteprotection.impl

import android.content.Context
import androidx.room.Room
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.impl.data.db.MaliciousSiteDao
import com.duckduckgo.malicioussiteprotection.impl.data.db.MaliciousSitesDatabase
import com.duckduckgo.malicioussiteprotection.impl.data.db.MaliciousSitesDatabase.Companion.ALL_MIGRATIONS
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import java.security.MessageDigest

@Module
@ContributesTo(AppScope::class)
class MaliciousSiteModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideMaliciousSiteProtectionDatabase(context: Context): MaliciousSitesDatabase {
        return Room.databaseBuilder(context, MaliciousSitesDatabase::class.java, "malicious_sites.db")
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideMaliciousSiteDao(database: MaliciousSitesDatabase): MaliciousSiteDao {
        return database.maliciousSiteDao()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideMessageDigest(): MessageDigest {
        return MessageDigest.getInstance("SHA-256")
    }
}
