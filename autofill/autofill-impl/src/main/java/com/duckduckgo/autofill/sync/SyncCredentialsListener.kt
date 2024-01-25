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

package com.duckduckgo.autofill.sync

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SingleInstanceIn(AppScope::class)
class SyncCredentialsListener @Inject constructor(
    private val credentialsSyncMetadata: CredentialsSyncMetadata,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) {
    private val delayedDeleteJobs = mutableMapOf<String, Job>()

    fun onCredentialAdded(id: Long) {
        val undoDeleteRequested = delayedDeleteJobs[id.toString()] != null
        if (undoDeleteRequested) {
            cancelAndDeleteJob(id.toString())
        } else {
            credentialsSyncMetadata.onEntityChanged(id)
        }
    }

    fun onCredentialsAdded(ids: List<Long>) {
        val undoDeleteRequested = delayedDeleteJobs[ids.joinToString()] != null
        if (undoDeleteRequested) {
            cancelAndDeleteJob(ids.joinToString())
        } else {
            credentialsSyncMetadata.onEntitiesChanged(ids)
        }
    }

    fun onCredentialUpdated(id: Long) {
        credentialsSyncMetadata.onEntityChanged(id)
    }

    fun onCredentialRemoved(id: Long) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            delay(SYNC_CREDENTIALS_DELETE_DELAY)
            credentialsSyncMetadata.onEntityRemoved(id)
        }.also {
            delayedDeleteJobs[id.toString()] = it
        }
    }

    fun onCredentialRemoved(ids: List<Long>) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            delay(SYNC_CREDENTIALS_DELETE_DELAY)
            credentialsSyncMetadata.onEntitiesRemoved(ids)
        }.also {
            delayedDeleteJobs[ids.joinToString()] = it
        }
    }

    private fun cancelAndDeleteJob(mapId: String) {
        delayedDeleteJobs[mapId]?.cancel()
        delayedDeleteJobs.remove(mapId)
    }

    companion object {
        const val SYNC_CREDENTIALS_DELETE_DELAY = 5000L
    }
}
