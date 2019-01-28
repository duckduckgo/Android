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

package com.duckduckgo.app.browser.rating.di

import android.content.Context
import com.duckduckgo.app.browser.rating.db.AppEnjoymentDao
import com.duckduckgo.app.browser.rating.db.AppEnjoymentDatabaseRepository
import com.duckduckgo.app.browser.rating.db.AppEnjoymentRepository
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.rating.AppEnjoyment
import com.duckduckgo.app.global.rating.AppEnjoymentManager
import com.duckduckgo.app.playstore.PlayStoreUtils
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import com.duckduckgo.app.usage.search.SearchCountDao
import dagger.Module
import dagger.Provides
import javax.inject.Singleton


@Module
class RatingModule {

    @Singleton
    @Provides
    fun appEnjoymentManager(
        playStoreUtils: PlayStoreUtils,
        searchCountDao: SearchCountDao,
        appDaysUsedRepository: AppDaysUsedRepository,
        appEnjoymentRepository: AppEnjoymentRepository,
        context: Context
    ): AppEnjoymentManager {
        return AppEnjoyment(playStoreUtils, searchCountDao, appDaysUsedRepository, appEnjoymentRepository, context)
    }

    @Provides
    fun playStoreUtils(): PlayStoreUtils {
        return PlayStoreUtils()
    }

    @Singleton
    @Provides
    fun appEnjoymentDao(database: AppDatabase): AppEnjoymentDao {
        return database.appEnjoymentDao()
    }

    @Singleton
    @Provides
    fun appEnjoymentRepository(appEnjoymentDao: AppEnjoymentDao): AppEnjoymentRepository {
        return AppEnjoymentDatabaseRepository(appEnjoymentDao)
    }

}