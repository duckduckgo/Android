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

import androidx.annotation.VisibleForTesting
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.engine.SyncChangesRequest
import com.duckduckgo.sync.api.engine.SyncChangesResponse
import com.duckduckgo.sync.api.engine.SyncableType
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.duckduckgo.sync.api.engine.SyncableType.CREDENTIALS
import com.duckduckgo.sync.impl.API_CODE
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.impl.pixels.SyncPixelValues.Feature.Autofill
import com.duckduckgo.sync.impl.pixels.SyncPixelValues.Feature.Bookmarks
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import org.json.JSONObject
import timber.log.Timber

interface SyncApiClient {

    fun patch(changes: SyncChangesRequest): Result<SyncChangesResponse>
    fun get(
        type: SyncableType,
        since: String,
    ): Result<SyncChangesResponse>
}

@ContributesBinding(AppScope::class)
class AppSyncApiClient @Inject constructor(
    private val syncStore: SyncStore,
    private val syncApi: SyncApi,
    private val syncPixels: SyncPixels,
) : SyncApiClient {

    override fun patch(changes: SyncChangesRequest): Result<SyncChangesResponse> {
        val token =
            syncStore.token.takeUnless { it.isNullOrEmpty() }
                ?: return Result.Error(reason = "Token Empty")

        if (changes.isEmpty()) {
            return Result.Error(reason = "Changes Empty")
        }

        val updates = JSONObject(changes.jsonString)
        Timber.d("Sync-Feature: patch data generated $updates")
        return when (val result = syncApi.patch(token, updates)) {
            is Result.Error -> {
                result
            }

            is Result.Success -> {
                if (result.data == null) {
                    Result.Success(SyncChangesResponse.empty(changes.type))
                } else {
                    val remoteChanges = mapResponse(changes.type, result.data)
                    Result.Success(remoteChanges)
                }
            }
        }
    }

    override fun get(
        type: SyncableType,
        since: String,
    ): Result<SyncChangesResponse> {
        val token =
            syncStore.token.takeUnless { it.isNullOrEmpty() }
                ?: return Result.Error(reason = "Token Empty")

        return when (type) {
            BOOKMARKS -> getBookmarks(type, token, since)
            CREDENTIALS -> getCredentials(type, token, since)
        }
    }

    private fun getBookmarks(
        type: SyncableType,
        token: String,
        since: String,
    ): Result<SyncChangesResponse> {
        return when (val result = syncApi.getBookmarks(token, since)) {
            is Result.Error -> {
                when (result.code) {
                    API_CODE.NOT_MODIFIED.code -> Result.Success(SyncChangesResponse.empty(type))
                    API_CODE.COUNT_LIMIT.code -> {
                        syncPixels.fireCountLimitPixel(Bookmarks)
                        Result.Error(result.code, result.reason)
                    }
                    else -> Result.Error(result.code, result.reason)
                }
            }

            is Result.Success -> {
                val remoteChanges = mapResponse(type, result.data)
                Result.Success(remoteChanges)
            }
        }
    }

    private fun getCredentials(
        type: SyncableType,
        token: String,
        since: String,
    ): Result<SyncChangesResponse> {
        return when (val result = syncApi.getCredentials(token, since)) {
            is Result.Error -> {
                when (result.code) {
                    API_CODE.NOT_MODIFIED.code -> Result.Success(SyncChangesResponse.empty(type))
                    API_CODE.COUNT_LIMIT.code -> {
                        syncPixels.fireCountLimitPixel(Autofill)
                        Result.Error(result.code, result.reason)
                    }
                    else -> Result.Error(result.code, result.reason)
                }
            }

            is Result.Success -> {
                val remoteChanges = mapResponse(type, result.data)
                Result.Success(remoteChanges)
            }
        }
    }

    @VisibleForTesting
    fun mapRequest(changes: List<SyncChangesRequest>): JSONObject {
        val bookmarkChanges = changes.first()
        return JSONObject(bookmarkChanges.jsonString)
    }

    @VisibleForTesting
    fun mapResponse(
        type: SyncableType,
        response: JSONObject,
    ): SyncChangesResponse {
        val jsonString = response.toString()
        Timber.d("Sync-Feature: $type response mapped to $jsonString")
        return SyncChangesResponse(type, jsonString)
    }
}
