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

package com.duckduckgo.malicioussiteprotection.impl

import android.annotation.SuppressLint
import android.content.Context
import androidx.room.Room
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.impl.data.db.MaliciousSiteDao
import com.duckduckgo.malicioussiteprotection.impl.data.db.MaliciousSitesDatabase
import com.duckduckgo.malicioussiteprotection.impl.data.db.MaliciousSitesDatabase.Companion.ALL_MIGRATIONS
import com.duckduckgo.malicioussiteprotection.impl.data.network.MaliciousSiteDatasetService
import com.duckduckgo.malicioussiteprotection.impl.data.network.MaliciousSiteService
import com.squareup.anvil.annotations.ContributesTo
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import okhttp3.Cache
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import javax.inject.Named
import javax.inject.Qualifier

@Module
@ContributesTo(AppScope::class)
class MaliciousSiteModule {

    @Retention(AnnotationRetention.BINARY)
    @Qualifier
    private annotation class InternalDatasetClient

    @Retention(AnnotationRetention.BINARY)
    @Qualifier
    private annotation class InternalClient

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideMaliciousSiteProtectionDatabase(context: Context): MaliciousSitesDatabase {
        return Room.databaseBuilder(context, MaliciousSitesDatabase::class.java, "malicious_sites.db")
            .addMigrations(*ALL_MIGRATIONS)
            .setQueryExecutor(Executors.newFixedThreadPool(4))
            .setTransactionExecutor(Executors.newSingleThreadExecutor())
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideMaliciousSiteDao(database: MaliciousSitesDatabase): MaliciousSiteDao {
        return database.maliciousSiteDao()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideMessageDigest(): MessageDigest {
        return MessageDigest.getInstance("SHA-256")
    }

    @Provides
    @InternalDatasetClient
    @SingleInstanceIn(AppScope::class)
    fun provideInternalDatasetCustomHttpClient(
        context: Context,
        @Named("api") okHttpClient: OkHttpClient,
    ): OkHttpClient {
        val cacheLocation = File(context.cacheDir, "datasetsCache")
        val cacheSize: Long = 20 * 1024 * 1024 // 20MB
        val cache = Cache(cacheLocation, cacheSize)
        return okHttpClient.newBuilder()
            .callTimeout(100, SECONDS)
            .cache(cache)
            .build()
    }

    @Provides
    @InternalClient
    @SingleInstanceIn(AppScope::class)
    fun provideInternalCustomHttpClient(
        context: Context,
        @Named("api") okHttpClient: OkHttpClient,
    ): OkHttpClient {
        val cacheLocation = File(context.cacheDir, "mspCache")
        val cacheSize: Long = 5 * 1024 * 1024 // 5MB
        val cache = Cache(cacheLocation, cacheSize)
        return okHttpClient.newBuilder()
            .callTimeout(5, SECONDS)
            .cache(cache)
            .build()
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    @SuppressLint("NoRetrofitCreateMethodCallDetector")
    fun providesMaliciousSiteDatasetService(
        @Named(value = "api") retrofit: Retrofit,
        @InternalDatasetClient customClient: Lazy<OkHttpClient>,
    ): MaliciousSiteDatasetService {
        val customRetrofit = retrofit.newBuilder()
            .callFactory { customClient.get().newCall(it) }
            .build()

        return customRetrofit.create(MaliciousSiteDatasetService::class.java)
    }

    @Provides
    @SingleInstanceIn(AppScope::class)
    @SuppressLint("NoRetrofitCreateMethodCallDetector")
    fun providesMaliciousSiteService(
        @Named(value = "api") retrofit: Retrofit,
        @InternalClient customClient: Lazy<OkHttpClient>,
    ): MaliciousSiteService {
        val customRetrofit = retrofit.newBuilder()
            .callFactory { customClient.get().newCall(it) }
            .build()

        return customRetrofit.create(MaliciousSiteService::class.java)
    }
}
