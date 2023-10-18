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

package com.duckduckgo.networkprotection.impl.rekey

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteCoroutineWorker
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import javax.inject.Inject
import kotlinx.coroutines.withContext

@ContributesWorker(AppScope::class)
class NetPRekeyWorker constructor(
    context: Context,
    workerParameters: WorkerParameters,
) : RemoteCoroutineWorker(context, workerParameters) {
    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var netPRekeyer: NetPRekeyer

    override suspend fun doRemoteWork(): Result {
        return withContext(dispatchers.io()) {
            netPRekeyer.doRekey()
            return@withContext Result.success()
        }
    }
}
