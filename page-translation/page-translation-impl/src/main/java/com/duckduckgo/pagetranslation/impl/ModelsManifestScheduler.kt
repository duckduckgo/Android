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

package com.duckduckgo.pagetranslation.impl

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.codegen.annotations.ContributesWorker
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
internal class ModelsManifestScheduler @Inject constructor(
    private val feature: PageTranslationFeature,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val workManager: WorkManager,
) : MainProcessLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {
        appScope.launch(dispatchers.io()) {
            feature.self().enabled().firstOrNull { it } ?: return@launch
            workManager.enqueueUniqueWork(
                "pageTranslationModelsManifest",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ModelsManifestWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }
}

@ContributesWorker(AppScope::class)
internal class ModelsManifestWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @Inject
    lateinit var feature: PageTranslationFeature

    @Inject
    lateinit var dispatchers: DispatcherProvider

    override suspend fun doWork(): Result = withContext(dispatchers.io()) {
        if (!feature.self().isEnabled()) return@withContext Result.success()

        val store = ModelsManifestStore(applicationContext, dispatchers)
        ModelsManifestFetcher(store, dispatchers).fetch()
    }
}
