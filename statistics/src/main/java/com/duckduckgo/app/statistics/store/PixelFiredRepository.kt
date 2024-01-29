/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.statistics.store

import com.duckduckgo.app.statistics.model.DailyPixelFired
import com.duckduckgo.app.statistics.model.UniquePixelFired
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject

interface PixelFiredRepository {
    suspend fun storeDailyPixelFiredToday(name: String)
    suspend fun hasDailyPixelFiredToday(name: String): Boolean
    suspend fun storeUniquePixelFired(name: String)
    suspend fun hasUniquePixelFired(name: String): Boolean
}

@ContributesBinding(AppScope::class)
class PixelFiredRepositoryImpl @Inject constructor(
    private val dailyPixelFiredDao: DailyPixelFiredDao,
    private val uniquePixelFiredDao: UniquePixelFiredDao,
    private val timeProvider: TimeProvider,
) : PixelFiredRepository {

    private val currentDate: LocalDate
        get() = LocalDate.ofInstant(timeProvider.getCurrentTime(), ZoneOffset.UTC)

    override suspend fun storeDailyPixelFiredToday(name: String) {
        dailyPixelFiredDao.insert(DailyPixelFired(name, currentDate))
    }

    override suspend fun hasDailyPixelFiredToday(name: String): Boolean =
        dailyPixelFiredDao.hasDailyPixelFired(name, currentDate)

    override suspend fun storeUniquePixelFired(name: String) {
        uniquePixelFiredDao.insert(UniquePixelFired(name))
    }

    override suspend fun hasUniquePixelFired(name: String): Boolean =
        uniquePixelFiredDao.hasUniquePixelFired(name)
}
