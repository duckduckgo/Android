/*
 * Copyright (c) 2017 DuckDuckGo
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

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.Room
import android.arch.persistence.room.RoomDatabase
import android.content.Context
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.settings.db.AppConfigurationDao
import dagger.Module
import dagger.Provides
import io.reactivex.schedulers.Schedulers
import javax.inject.Singleton

@Module
class DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "app.db")
                .fallbackToDestructiveMigration()
                .addCallback(initialDbCreationCallback())
                .build()
    }

    /**
     * Use this callback as a chance to seed the database with initial data.
     */
    private fun initialDbCreationCallback(): RoomDatabase.Callback {
        return object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // insert the data on the IO Thread

                Schedulers.io().scheduleDirect({
                    //language=RoomSql
                    db.execSQL("INSERT INTO app_configuration (key, appConfigurationDownloaded) VALUES ('${AppConfigurationDao.KEY}', 'false')")
                })
            }
        }
    }

    @Provides
    fun provideHttpsUpgradeDomainDao(database: AppDatabase) =
            database.httpsUpgradeDomainDao()

    @Provides
    fun provideDisconnectTrackDao(database: AppDatabase) = database.trackerDataDao()

}