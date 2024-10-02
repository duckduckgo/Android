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

package com.duckduckgo.privacy.dashboard.impl.di

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.RequestState
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.RequestState.Allowed
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.RequestState.Blocked
import com.duckduckgo.privacy.dashboard.impl.ui.ScreenKind
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import javax.inject.Named

@Module
@ContributesTo(AppScope::class)
object JsonModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    @Named("privacyDashboard")
    fun moshi(moshi: Moshi): Moshi {
        return moshi.newBuilder()
            .add(
                PolymorphicJsonAdapterFactory.of(RequestState::class.java, "state")
                    .withSubtype(Blocked::class.java, "blocked")
                    .withSubtype(Allowed::class.java, "allowed"),
            )
            .add(ScreenKindJsonAdapter())
            .build()
    }
}

class ScreenKindJsonAdapter {
    @FromJson
    fun fromJson(value: String): ScreenKind? =
        ScreenKind.entries.find { it.value == value }

    @ToJson
    fun toJson(screen: ScreenKind?): String? =
        screen?.value
}
