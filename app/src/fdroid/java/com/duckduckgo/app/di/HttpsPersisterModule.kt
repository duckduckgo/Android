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

package com.duckduckgo.app.di

import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.store.BinaryDataStore
import com.duckduckgo.app.httpsupgrade.store.HttpsBloomFilterSpecDao
import com.duckduckgo.app.httpsupgrade.store.HttpsDataPersister
import com.duckduckgo.app.httpsupgrade.store.HttpsFalsePositivesDao
import com.duckduckgo.httpsupgrade.store.FDroidHttpsDataPersister
import dagger.Module
import dagger.Provides

@Module
class HttpsPersisterModule {

    @Provides
    fun providesHttpsDataPersister(
        binaryDataStore: BinaryDataStore,
        httpsBloomSpecDao: HttpsBloomFilterSpecDao,
        httpsFalsePositivesDao: HttpsFalsePositivesDao,
        appDatabase: AppDatabase
    ): HttpsDataPersister {
        return FDroidHttpsDataPersister(binaryDataStore, httpsBloomSpecDao, httpsFalsePositivesDao, appDatabase)
    }

}
