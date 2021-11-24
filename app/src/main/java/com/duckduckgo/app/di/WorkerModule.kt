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

package com.duckduckgo.app.di

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPluginPoint
import com.duckduckgo.di.scopes.AppObjectGraph
import dagger.Module
import dagger.Provides
import dagger.SingleIn

@Module
class WorkerModule {

    @Provides
    @SingleIn(AppObjectGraph::class)
    fun workManager(context: Context, workerFactory: WorkerFactory): WorkManager {
        val config = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
        WorkManager.initialize(context, config)
        return WorkManager.getInstance(context)
    }

    @Provides
    @SingleIn(AppObjectGraph::class)
    fun workerFactory(
        workerInjectorPluginPoint: WorkerInjectorPluginPoint,
    ): WorkerFactory {
        return DaggerWorkerFactory(workerInjectorPluginPoint)
    }
}
