/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.statistics.wideevents

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@ContributesMultibinding(AppScope::class)
class CompletedWideEventsProcessor @Inject constructor(
    private val wideEventRepository: WideEventRepository,
    private val wideEventSender: WideEventSender,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val wideEventFeature: WideEventFeature,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch {
            runCatching {
                if (!isFeatureEnabled()) return@runCatching

                wideEventRepository
                    .getCompletedWideEventIdsFlow()
                    .conflate()
                    .collect { ids ->
                        // Process events in chunks to avoid querying too many events at once.
                        ids.chunked(100).forEach { idsChunk ->
                            processCompletedWideEvents(idsChunk.toSet())
                        }
                    }
            }
        }
    }

    private suspend fun processCompletedWideEvents(wideEventIds: Set<Long>) {
        wideEventRepository.getWideEvents(wideEventIds).forEach { event ->
            wideEventSender.sendWideEvent(event)
            wideEventRepository.deleteWideEvent(event.id)
        }
    }

    private suspend fun isFeatureEnabled(): Boolean =
        withContext(dispatcherProvider.io()) {
            wideEventFeature.self().isEnabled()
        }
}
