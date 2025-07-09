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
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.common.utils.plugins.worker.WorkerInjectorPlugin
import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.asLog
import logcat.logcat

class DaggerWorkerFactory(
    private val workerInjectorPluginPoint: PluginPoint<WorkerInjectorPlugin>,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        try {
            val workerClass = Class.forName(workerClassName).asSubclass(ListenableWorker::class.java)
            val constructor = workerClass.getDeclaredConstructor(Context::class.java, WorkerParameters::class.java)
            val instance = constructor.newInstance(appContext, workerParameters)

            workerInjectorPluginPoint.getPlugins().forEach { plugin ->
                if (plugin.inject(instance)) {
                    logcat(INFO) { "Injected using plugin $workerClassName" }
                    return@forEach
                }
                logcat(INFO) { "No injection required for worker $workerClassName" }
            }

            return instance
        } catch (exception: Exception) {
            logcat(ERROR) { "Worker $workerClassName could not be created: ${exception.asLog()}" }
            return null
        }
    }
}
