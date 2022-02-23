/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl.di

import androidx.work.ListenableWorker
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.remote.messaging.impl.RemoteMessagingConfigDownloadWorker
import com.duckduckgo.remote.messaging.impl.RemoteMessagingConfigDownloader
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import javax.inject.Provider

@ContributesMultibinding(AppScope::class)
class RemoteMessagingConfigDownloadWorkerPlugin @Inject constructor(
    private val downloader: Provider<RemoteMessagingConfigDownloader>,
    private val dispatcherProvider: Provider<DispatcherProvider>
) : WorkerInjectorPlugin {
    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is RemoteMessagingConfigDownloadWorker) {
            worker.downloader = downloader.get()
            worker.dispatcherProvider = dispatcherProvider.get()
            return true
        }
        return false
    }
}
