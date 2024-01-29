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

import android.content.Context
import androidx.room.Room
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(AppScope::class)
class StatisticsDatabaseModule {
    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideStatisticsDatabase(context: Context): StatisticsDatabase =
        Room
            .databaseBuilder(
                context = context,
                klass = StatisticsDatabase::class.java,
                name = "pixels.db",
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideDailyPixelFiredDao(db: StatisticsDatabase): DailyPixelFiredDao =
        db.dailyPixelFiredDao()

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideUniquePixelFiredDao(db: StatisticsDatabase): UniquePixelFiredDao =
        db.uniquePixelFiredDao()
}
