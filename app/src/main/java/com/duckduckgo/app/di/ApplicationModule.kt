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

import android.app.Application
import android.content.Context
import com.duckduckgo.app.global.currentProcessName
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
abstract class ApplicationModule {

    @SingleInstanceIn(AppScope::class)
    @Binds
    abstract fun bindContext(application: Application): Context
}

@Module
@ContributesTo(AppScope::class)
object ProcessNameModule {

    @SingleInstanceIn(AppScope::class)
    @Provides
    @ProcessName
    fun provideProcessName(context: Context): String {
        val process = runCatching {
            context.currentProcessName?.substringAfter(delimiter = context.packageName, missingDelimiterValue = "UNKNOWN") ?: "UNKNOWN"
        }.getOrDefault("ERROR")

        // When is the main process 'currentProcessName' returns the package name and so `process` is empty string
        return process.ifEmpty {
            "main"
        }
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    @IsMainProcess
    fun providerIsMainProcess(@ProcessName processName: String): Boolean {
        return processName == "main"
    }
}
