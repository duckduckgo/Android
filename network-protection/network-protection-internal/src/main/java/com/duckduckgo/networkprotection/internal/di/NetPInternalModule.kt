/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.internal.di

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.store.remote_config.*
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(AppScope::class)
object NetPInternalModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideNetPInternalConfigDatabase(context: Context): NetPInternalConfigDatabase {
        return NetPInternalConfigDatabase.create(context)
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    @InternalNetPConfigTogglesDao
    fun provideNetPConfigTogglesDao(netPInternalConfigDatabase: NetPInternalConfigDatabase): NetPConfigTogglesDao {
        return netPInternalConfigDatabase.configTogglesDao()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideNetPServersDao(netPInternalConfigDatabase: NetPInternalConfigDatabase): NetPServersDao {
        return netPInternalConfigDatabase.serversDao()
    }

    @Provides
    fun provideNetPServerRepository(netPServersDao: NetPServersDao): NetPServerRepository {
        return NetPServerRepository(netPServersDao)
    }
}
