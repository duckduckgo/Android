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

package com.duckduckgo.subscriptions.impl.repository

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.store.RebrandingDataStore
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface RebrandingRepository {
    suspend fun isRebrandingBannerShown(): Boolean
    suspend fun setRebrandingBannerAsViewed()
}

@ContributesBinding(AppScope::class)
class RebrandingRepositoryImpl @Inject constructor(
    private val rebrandingDataStore: RebrandingDataStore,
    private val dispatcherProvider: DispatcherProvider,
) : RebrandingRepository {

    override suspend fun isRebrandingBannerShown(): Boolean = withContext(dispatcherProvider.io()) {
        rebrandingDataStore.rebrandingBannerShown
    }

    override suspend fun setRebrandingBannerAsViewed() = withContext(dispatcherProvider.io()) {
        rebrandingDataStore.rebrandingBannerShown = true
    }
}
