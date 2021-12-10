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

package com.duckduckgo.app.statistics

import com.duckduckgo.app.di.StatisticsLibraryConfigModule
import com.duckduckgo.app.statistics.config.StatisticsLibraryConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(
    scope = AppScope::class,
    replaces = [StatisticsLibraryConfigModule::class]
)
class InternalStatisticsLibraryConfigModule {
    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideStatisticsLibraryConfig(): StatisticsLibraryConfig {
        return object : StatisticsLibraryConfig {
            override fun shouldFirePixelsAsDev(): Boolean {
                // for internal builds we always want to pixel as dev so that we can separate
                // internal users from real production users
                return true
            }
        }
    }
}
