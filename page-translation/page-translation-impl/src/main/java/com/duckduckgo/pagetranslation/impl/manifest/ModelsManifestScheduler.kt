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

import androidx.lifecycle.LifecycleOwner
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pagetranslation.impl.PageTranslationDeviceSupport
import com.duckduckgo.pagetranslation.impl.PageTranslationFeature
import dev.zacsweers.metro.ContributesIntoSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class ModelsManifestScheduler @Inject constructor(
    private val feature: PageTranslationFeature,
    private val deviceSupport: PageTranslationDeviceSupport,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val workManager: WorkManager,
) : MainProcessLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {
        if (!deviceSupport.isSupported()) return

        appScope.launch(dispatchers.io()) {
            feature.translation().enabled().firstOrNull { isEnabled -> isEnabled } ?: return@launch

            workManager.enqueueUniqueWork(
                MANIFEST_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ModelsManifestWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }
    }

    private companion object {
        const val MANIFEST_WORK_NAME = "pageTranslationModelsManifest"
    }
}
