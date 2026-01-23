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

package com.duckduckgo.duckchat.impl.messaging.sync

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.SyncState
import com.duckduckgo.sync.api.SyncStateMonitor
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import logcat.logcat
import org.json.JSONObject
import javax.inject.Inject

/**
 * Interface for observing sync status changes and providing payloads for WebView communication.
 */
interface SyncStatusChangedObserver {
    /**
     * A flow of sync status changed events that emits JSONObject payloads
     * whenever the sync signed-in state changes. Consumers can collect this flow to
     * send submitSyncStatusChanged events to their WebViews.
     */
    val syncStatusChangedEvents: Flow<JSONObject>
}

/**
 * Observes sync state changes; only emits when the signed-in status actually changes (OFF <-> non-OFF),
 * ignoring intermediate states like IN_PROGRESS and FAILED transitions.
 *
 * Note: We drop(1) to skip the initial emission because:
 * The SyncStateMonitor may emit an initial OFF state before fully initializing
 * This observer is meant to push state CHANGES, not the initial state
 */
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSyncStatusChangedObserver @Inject constructor(
    private val syncStatusHelper: SyncStatusHelper,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    syncStateMonitor: SyncStateMonitor,
    dispatcherProvider: DispatcherProvider,
) : SyncStatusChangedObserver {

    override val syncStatusChangedEvents: Flow<JSONObject> = syncStateMonitor.syncState()
        .map { it.isSignedIn() }
        .distinctUntilChanged() // Only emit when signed-in status changes
        .drop(1) // Skip initial emission as this is only for changes
        .onEach { logcat { "DuckChat-Sync: signed-in status changed to $it" } }
        .map { syncStatusHelper.buildSyncStatusPayload() }
        .flowOn(dispatcherProvider.io())
        .shareIn(appCoroutineScope, SharingStarted.WhileSubscribed(), replay = 0)

    /**
     * Returns true if the sync state indicates the user is signed in.
     * OFF means not signed in, all other states are treated as meaning signed in.
     */
    private fun SyncState.isSignedIn(): Boolean = this != SyncState.OFF
}
