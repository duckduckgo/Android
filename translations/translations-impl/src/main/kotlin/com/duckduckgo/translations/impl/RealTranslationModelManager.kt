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

package com.duckduckgo.translations.impl

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.translations.api.ModelDownloadStatus
import com.duckduckgo.translations.api.TranslationModelManager
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealTranslationModelManager @Inject constructor(
    private val modelStore: ModelStore,
    private val dispatchers: DispatcherProvider,
) : TranslationModelManager {

    override suspend fun isModelDownloaded(sourceLanguage: String, targetLanguage: String): Boolean =
        withContext(dispatchers.io()) {
            modelStore.isDownloaded(sourceLanguage, targetLanguage)
        }

    override fun downloadModel(sourceLanguage: String, targetLanguage: String): Flow<ModelDownloadStatus> = flow {
        // The actual GCS download is wired up in the next step.
        emit(ModelDownloadStatus.Failed("Model download is not wired up yet"))
    }
}
