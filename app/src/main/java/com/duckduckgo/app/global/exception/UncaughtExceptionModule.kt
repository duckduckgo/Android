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

package com.duckduckgo.app.global.exception

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.AlertingUncaughtExceptionHandler
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.device.DeviceInfo
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
class UncaughtExceptionModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun uncaughtWebViewExceptionRepository(
        uncaughtExceptionDao: UncaughtExceptionDao,
        rootExceptionFinder: RootExceptionFinder,
        deviceInfo: DeviceInfo
    ): UncaughtExceptionRepository {
        return UncaughtExceptionRepositoryDb(uncaughtExceptionDao, rootExceptionFinder, deviceInfo)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun alertingUncaughtExceptionHandler(
        offlinePixelCountDataStore: OfflinePixelCountDataStore,
        uncaughtExceptionRepository: UncaughtExceptionRepository,
        dispatcherProvider: DispatcherProvider,
        @AppCoroutineScope appCoroutineScope: CoroutineScope,
        appBuildConfig: AppBuildConfig
    ): AlertingUncaughtExceptionHandler {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        return AlertingUncaughtExceptionHandler(
            originalHandler,
            offlinePixelCountDataStore,
            uncaughtExceptionRepository,
            dispatcherProvider,
            appCoroutineScope,
            appBuildConfig
        )
    }

    @Provides
    fun rootExceptionFinder() = RootExceptionFinder()
}
