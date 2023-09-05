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

package com.duckduckgo.sync.impl

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.statistics.api.BrowserFeatureStateReporterPlugin
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.SyncState
import com.duckduckgo.sync.api.SyncState.FAILED
import com.duckduckgo.sync.api.SyncState.OFF
import com.duckduckgo.sync.api.SyncStateMonitor
import com.duckduckgo.sync.impl.engine.SyncStateRepository
import com.duckduckgo.sync.store.SyncStore
import com.duckduckgo.sync.store.model.SyncAttempt
import com.duckduckgo.sync.store.model.SyncAttemptState.FAIL
import com.duckduckgo.sync.store.model.SyncAttemptState.IN_PROGRESS
import com.duckduckgo.sync.store.model.SyncAttemptState.SUCCESS
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber

@ContributesMultibinding(scope = AppScope::class, boundType = BrowserFeatureStateReporterPlugin::class)
@ContributesBinding(scope = AppScope::class, boundType = SyncStateMonitor::class)
class RealSyncStateMonitor @Inject constructor(
    private val syncStore: SyncStore,
    private val syncStateRepository: SyncStateRepository,
    private val dispatcherProvider: DispatcherProvider,
) : SyncStateMonitor, BrowserFeatureStateReporterPlugin {

    override fun syncState(): Flow<SyncState> {
        return syncStateRepository.state()
            .combine(syncStore.isSignedInFlow()) { attempt, signedIn ->
                mapState(attempt, signedIn)
            }.distinctUntilChanged()
            .flowOn(dispatcherProvider.io())
    }

    private fun mapState(
        attempt: SyncAttempt,
        signedIn: Boolean,
    ): SyncState {
        return if (signedIn) {
            if (attempt == null) {
                Timber.d("Sync-Feature: Sync Monitor signed in, READY state")
                SyncState.READY
            } else {
                Timber.d("Sync-Feature: Sync Monitor signed in, sync in ${attempt.state} state")
                when (attempt.state) {
                    IN_PROGRESS -> SyncState.IN_PROGRESS
                    SUCCESS -> SyncState.READY
                    FAIL -> FAILED
                }
            }
        } else {
            Timber.d("Sync-Feature: Sync Monitor not signed in, OFF state")
            return OFF
        }
    }

    override fun featureState(): Pair<Boolean, String> {
        return Pair(syncStore.isSignedIn(), PixelParameter.SYNC)
    }
}
