/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.pagetranslation.impl.manifest

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.duckduckgo.codegen.annotations.ContributesWorker
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pagetranslation.impl.PageTranslationDeviceSupport
import com.duckduckgo.pagetranslation.impl.PageTranslationFeature
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesWorker(AppScope::class)
internal class ModelsManifestWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @Inject
    lateinit var feature: PageTranslationFeature

    @Inject
    lateinit var deviceSupport: PageTranslationDeviceSupport

    @Inject
    lateinit var fetcher: Lazy<ModelsManifestFetcher>

    @Inject
    lateinit var dispatchers: DispatcherProvider

    override suspend fun doWork(): Result = withContext(dispatchers.io()) {
        if (!feature.translation().isEnabled() || !deviceSupport.isSupported()) return@withContext Result.success()

        fetcher.value.fetch()
    }
}
