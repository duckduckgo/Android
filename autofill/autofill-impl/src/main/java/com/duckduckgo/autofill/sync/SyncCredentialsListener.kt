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

import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
class SyncCredentialsListener @Inject constructor(
    private val credentialsSyncMetadata: CredentialsSyncMetadata,
) {

    fun onCredentialAdded(id: Long) {
        credentialsSyncMetadata.onEntityChanged(id)
    }

    fun onCredentialsAdded(ids: List<Long>) {
        credentialsSyncMetadata.onEntitiesChanged(ids)
    }

    fun onCredentialUpdated(id: Long) {
        credentialsSyncMetadata.onEntityChanged(id)
    }

    fun onCredentialRemoved(id: Long) {
        credentialsSyncMetadata.onEntityRemoved(id)
    }

    fun onCredentialRemoved(ids: List<Long>) {
        credentialsSyncMetadata.onEntitiesRemoved(ids)
    }
}
