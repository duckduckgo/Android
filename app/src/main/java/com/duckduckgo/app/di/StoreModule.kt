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

import androidx.lifecycle.LifecycleObserver
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.fire.UnsentForgetAllPixelStoreSharedPreferences
import com.duckduckgo.app.global.events.db.*
import com.duckduckgo.app.global.install.AppInstallSharedPreferences
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.store.AppUserStageStore
import com.duckduckgo.app.onboarding.store.OnboardingSharedPreferences
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.privacy.store.TermsOfServiceRawStore
import com.duckduckgo.app.privacy.store.TermsOfServiceStore
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.duckduckgo.app.statistics.store.OfflinePixelCountSharedPreferences
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.app.statistics.store.StatisticsSharedPreferences
import com.duckduckgo.app.tabs.db.TabsDbSanitizer
import com.duckduckgo.app.tabs.model.TabDataRepository
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.widget.FavoritesObserver
import com.duckduckgo.mobile.android.ui.store.ThemingDataStore
import com.duckduckgo.mobile.android.ui.store.ThemingSharedPreferences
import com.duckduckgo.widget.AppWidgetThemePreferences
import com.duckduckgo.widget.WidgetPreferences
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet

@Module
abstract class StoreModule {

    @Binds
    abstract fun bindStatisticsStore(
        statisticsStore: StatisticsSharedPreferences
    ): StatisticsDataStore

    @Binds abstract fun bindThemingStore(themeDataStore: ThemingSharedPreferences): ThemingDataStore

    @Binds
    abstract fun bindOnboardingStore(onboardingStore: OnboardingSharedPreferences): OnboardingStore

    @Binds
    abstract fun bindTermsOfServiceStore(
        termsOfServiceStore: TermsOfServiceRawStore
    ): TermsOfServiceStore

    @Binds abstract fun bindTabReposistory(tabRepository: TabDataRepository): TabRepository

    @Binds abstract fun bindAppInstallStore(store: AppInstallSharedPreferences): AppInstallStore

    @Binds
    @IntoSet
    abstract fun bindAppInstallStoreObserver(appInstallStore: AppInstallStore): LifecycleObserver

    @Binds
    abstract fun bindDataClearingStore(
        store: UnsentForgetAllPixelStoreSharedPreferences
    ): UnsentForgetAllPixelStore

    @Binds
    abstract fun bindOfflinePixelDataStore(
        store: OfflinePixelCountSharedPreferences
    ): OfflinePixelCountDataStore

    @Binds abstract fun bindUserStageStore(userStageStore: AppUserStageStore): UserStageStore

    @Binds
    @IntoSet
    abstract fun bindUserStageStoreObserver(userStageStore: UserStageStore): LifecycleObserver

    @Binds abstract fun bindUserEventsStore(userEventsStore: AppUserEventsStore): UserEventsStore

    @Binds
    @IntoSet
    abstract fun bindTabsDbSanitizerObserver(tabsDbSanitizer: TabsDbSanitizer): LifecycleObserver

    @Binds
    @IntoSet
    abstract fun bindFavoritesObserver(favoritesObserver: FavoritesObserver): LifecycleObserver

    @Binds abstract fun bindWidgetPreferences(store: AppWidgetThemePreferences): WidgetPreferences
}
