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

package com.duckduckgo.mobile.android.vpn.feature

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.store.AppTpFeatureToggleRepository
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@ContributesTo(AppScope::class)
@Module
object AppTpFeatureToggleRepositoryModule {
    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideAppTpFeatureToggleRepository(context: Context): AppTpFeatureToggleRepository {
        return AppTpFeatureToggleRepository.create(context)
    }
}
