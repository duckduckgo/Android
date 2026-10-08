/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.privacy.config.internal

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.internal.PrivacyConfigInternalLoader.Companion.EXTRA_URL
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject

@ContributesWorker(AppScope::class)
class PrivacyConfigOverrideWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    @Inject lateinit var loader: PrivacyConfigInternalLoader

    @Inject lateinit var dispatcherProvider: DispatcherProvider

    override suspend fun doWork(): Result = withContext(dispatcherProvider.io()) {
        // Completing command processing successfully keeps later queued commands runnable.
        val applied = loader.handleCommand(inputData.getString(KEY_ACTION), inputData.getString(EXTRA_URL))
        if (applied) {
            Result.success()
        } else {
            logcat(WARN) { "Privacy config override command failed" }
            Result.success()
        }
    }

    companion object {
        const val KEY_ACTION = "action"
    }
}
