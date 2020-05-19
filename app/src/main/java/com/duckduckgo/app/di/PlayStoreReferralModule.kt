/*
 * Copyright (c) 2019 DuckDuckGo
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
import android.content.pm.PackageManager
import com.duckduckgo.app.referral.*
import com.duckduckgo.app.statistics.VariantManager
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module
class PlayStoreReferralModule {

    @Provides
    fun appInstallationReferrerParser(): AppInstallationReferrerParser {
        return QueryParamReferrerParser()
    }

    @Provides
    @Singleton
    fun appInstallationReferrerStateListener(
        context: Context,
        packageManager: PackageManager,
        appInstallationReferrerParser: AppInstallationReferrerParser,
        appReferrerDataStore: AppReferrerDataStore,
        variantManager: VariantManager
    ): AppInstallationReferrerStateListener {
        return PlayStoreAppReferrerStateListener(context, packageManager, appInstallationReferrerParser, appReferrerDataStore, variantManager)
    }

    @Provides
    @Singleton
    fun appReferrerDataStore(context: Context): AppReferrerDataStore {
        return AppReferenceSharePreferences(context)
    }
}
