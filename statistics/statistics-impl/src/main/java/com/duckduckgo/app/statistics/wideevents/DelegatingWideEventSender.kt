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

import com.duckduckgo.app.statistics.wideevents.db.WideEventRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Named

@ContributesBinding(AppScope::class)
class DelegatingWideEventSender @Inject constructor(
    @Named("pixel") private val pixelWideEventSender: WideEventSender,
    @Named("api") private val apiWideEventSender: WideEventSender,
    private val wideEventFeature: WideEventFeature,
    private val dispatcherProvider: DispatcherProvider,
) : WideEventSender {

    override suspend fun sendWideEvent(event: WideEventRepository.WideEvent) {
        if (shouldUseApiSender()) {
            apiWideEventSender.sendWideEvent(event)
        }
        if (shouldUsePixelSender()) {
            pixelWideEventSender.sendWideEvent(event)
        }
    }

    private suspend fun shouldUsePixelSender(): Boolean = withContext(dispatcherProvider.io()) {
        wideEventFeature.sendWideEventsViaPixels().isEnabled()
    }

    private suspend fun shouldUseApiSender(): Boolean = withContext(dispatcherProvider.io()) {
        wideEventFeature.sendWideEventsViaPost().isEnabled()
    }
}
