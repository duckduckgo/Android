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

package com.duckduckgo.sync.impl.engine

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.SyncChanges
import com.duckduckgo.sync.api.SyncableType.BOOKMARKS
import com.duckduckgo.sync.impl.SyncDataResponse
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.parser.SyncDataRequest
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import timber.log.Timber
import javax.inject.Inject

interface SyncApiClient {

    fun patch(changes: List<SyncChanges>): Result<SyncDataResponse>

}

@ContributesBinding(AppScope::class)
class AppSyncApiClient @Inject constructor(
    private val syncStore: SyncStore,
    private val syncApi: SyncApi,
) : SyncApiClient {
    override fun patch(changes: List<SyncChanges>): Result<SyncDataResponse> {
        val token =
            syncStore.token.takeUnless { it.isNullOrEmpty() }
                ?: return Result.Error(reason = "Token Empty")

        if (changes.isEmpty()){
            return Result.Error(reason = "Changes Empty")
        }

        val localChangesJSON = mapChanges(changes)
        Timber.d("Sync: patch data generated $localChangesJSON")
        val localChanges = Adapters.patchAdapter.fromJson(localChangesJSON)!!
        return when (val result = syncApi.patch(token, localChanges)) {
            is Result.Error -> {
                result
            }

            is Result.Success -> {
                Result.Success(result.data)
            }
        }
    }

    private fun mapChanges(changes: List<SyncChanges>): String {
        return changes.first { it.type == BOOKMARKS }.updatesJSON
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().build()
            val patchAdapter: JsonAdapter<SyncDataRequest> =
                moshi.adapter(SyncDataRequest::class.java)
        }
    }
}
