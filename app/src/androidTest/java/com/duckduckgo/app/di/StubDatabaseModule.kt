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
import android.webkit.WebViewDatabase
import androidx.room.Room
import com.duckduckgo.app.browser.httpauth.RealWebViewHttpAuthStore
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.httpauth.db.HttpAuthDatabase
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.db.AppDatabase
import dagger.Module
import dagger.Provides
import javax.inject.Singleton

@Module(includes = [DaoModule::class])
class StubDatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(context: Context): AppDatabase {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Provides
    @Singleton
    fun provideWebViewHttpAuthStore(
        context: Context,
        fireproofWebsiteDao: FireproofWebsiteDao
    ): WebViewHttpAuthStore {
        val db = Room.inMemoryDatabaseBuilder(context, HttpAuthDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        return RealWebViewHttpAuthStore(DefaultDispatcherProvider(), fireproofWebsiteDao, WebViewDatabase.getInstance(context), db.httpAuthDao())
    }
}
