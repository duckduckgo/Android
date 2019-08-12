/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.di

import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.fire.UnsentForgetAllPixelStoreSharedPreferences
import com.duckduckgo.app.global.install.AppInstallSharedPreferences
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.OnboardingSharedPreferences
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.privacy.store.*
import com.duckduckgo.app.statistics.store.OfflinePixelDataStore
import com.duckduckgo.app.statistics.store.OfflinePixelSharedPreferences
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.statistics.store.StatisticsSharedPreferences
import com.duckduckgo.app.tabs.model.TabDataRepository
import com.duckduckgo.app.tabs.model.TabRepository
import dagger.Binds
import dagger.Module

@Module
abstract class StoreModule {

    @Binds
    abstract fun bindStatisticsStore(statisticsStore: StatisticsSharedPreferences): StatisticsDataStore

    @Binds
    abstract fun bindOnboardingStore(onboardingStore: OnboardingSharedPreferences): OnboardingStore

    @Binds
    abstract fun bindPrivacySettingsStore(privacySettingsStore: PrivacySettingsSharedPreferences): PrivacySettingsStore

    @Binds
    abstract fun bindTermsOfServiceStore(termsOfServiceStore: TermsOfServiceRawStore): TermsOfServiceStore

    @Binds
    abstract fun bindTabReposistory(tabRepository: TabDataRepository): TabRepository

    @Binds
    abstract fun bindAppInstallStore(store: AppInstallSharedPreferences): AppInstallStore

    @Binds
    abstract fun bindDataClearingStore(store: UnsentForgetAllPixelStoreSharedPreferences): UnsentForgetAllPixelStore

    @Binds
    abstract fun bindOfflinePixelDataStore(store: OfflinePixelSharedPreferences): OfflinePixelDataStore

    @Binds
    abstract fun bindPrevalenceStore(store: PrevalenceRawStore): PrevalenceStore

}