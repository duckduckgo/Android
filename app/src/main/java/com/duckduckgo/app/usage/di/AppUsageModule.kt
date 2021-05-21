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

package com.duckduckgo.app.usage.di

import androidx.lifecycle.LifecycleObserver
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.usage.app.AppDaysUsedDao
import com.duckduckgo.app.usage.app.AppDaysUsedDatabaseRepository
import com.duckduckgo.app.usage.app.AppDaysUsedRecorder
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
class AppUsageModule {

    @Provides
    @Singleton
    @IntoSet
    fun appDaysUsedRecorderObserver(
        appDaysUsedRepository: AppDaysUsedRepository,
        @AppCoroutineScope appCoroutineScope: CoroutineScope
    ): LifecycleObserver {
        return AppDaysUsedRecorder(appDaysUsedRepository, appCoroutineScope)
    }

    @Provides
    @Singleton
    fun appDaysUsedRespository(appDaysUsedDao: AppDaysUsedDao): AppDaysUsedRepository {
        return AppDaysUsedDatabaseRepository(appDaysUsedDao)
    }
}
