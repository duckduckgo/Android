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

import android.content.Context
import com.duckduckgo.app.browser.WebDataManager
import com.duckduckgo.app.entities.EntityMapping
import com.duckduckgo.app.fire.*
import com.duckduckgo.app.global.file.FileDeleter
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.global.view.ClearDataAction
import com.duckduckgo.app.global.view.ClearPersonalDataAction
import com.duckduckgo.app.privacy.HistoricTrackerBlockingObserver
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.PrivacyPracticesImpl
import com.duckduckgo.app.privacy.store.PrivacySettingsStore
import com.duckduckgo.app.privacy.store.TermsOfServiceStore
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabRepository
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class PrivacyModule {

    @Provides
    @Singleton
    fun privacyPractices(termsOfServiceStore: TermsOfServiceStore, entityMapping: EntityMapping): PrivacyPractices =
        PrivacyPracticesImpl(termsOfServiceStore, entityMapping)

    @Provides
    fun clearDataAction(
        context: Context,
        dataManager: WebDataManager,
        clearingStore: UnsentForgetAllPixelStore,
        tabRepository: TabRepository,
        settingsDataStore: SettingsDataStore,
        cookieManager: DuckDuckGoCookieManager,
        appCacheClearer: AppCacheClearer
    ): ClearDataAction {
        return ClearPersonalDataAction(context, dataManager, clearingStore, tabRepository, settingsDataStore, cookieManager, appCacheClearer)
    }

    @Provides
    fun backgroundTimeKeeper(): BackgroundTimeKeeper {
        return DataClearerTimeKeeper()
    }

    @Provides
    @Singleton
    fun automaticDataClearer(
        settingsDataStore: SettingsDataStore,
        clearDataAction: ClearDataAction,
        dataClearerTimeKeeper: BackgroundTimeKeeper
    ): DataClearer {
        return AutomaticDataClearer(settingsDataStore, clearDataAction, dataClearerTimeKeeper)
    }

    @Provides
    @Singleton
    fun historicTrackerBlockingObserver(
        appInstallStore: AppInstallStore,
        privacySettingsStore: PrivacySettingsStore,
        pixel: Pixel
    ): HistoricTrackerBlockingObserver =
        HistoricTrackerBlockingObserver(appInstallStore, privacySettingsStore, pixel)

    @Provides
    @Singleton
    fun appCacheCleaner(context: Context, fileDeleter: FileDeleter): AppCacheClearer {
        return AndroidAppCacheClearer(context, fileDeleter)
    }
}