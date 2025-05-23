/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.sync.impl.error

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.engine.SyncableType
import com.duckduckgo.sync.impl.API_CODE
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.store.model.SyncApiErrorType.OBJECT_LIMIT_EXCEEDED
import com.duckduckgo.sync.store.model.SyncApiErrorType.REQUEST_SIZE_LIMIT_EXCEEDED
import com.duckduckgo.sync.store.model.SyncApiErrorType.TOO_MANY_REQUESTS
import com.duckduckgo.sync.store.model.SyncApiErrorType.VALIDATION_ERROR
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.logcat

interface SyncApiErrorRecorder {

    fun record(
        feature: SyncableType,
        apiError: Error,
    )
}

@ContributesBinding(AppScope::class)
class RealSyncApiErrorRecorder @Inject constructor(
    private val syncPixels: SyncPixels,
    private val syncApiErrorRepository: SyncApiErrorRepository,
) : SyncApiErrorRecorder {
    override fun record(
        feature: SyncableType,
        apiError: Error,
    ) {
        logcat { "Sync-Error: Recording API Error for $feature as $apiError" }
        when (apiError.code) {
            API_CODE.COUNT_LIMIT.code -> {
                syncApiErrorRepository.addError(feature, OBJECT_LIMIT_EXCEEDED)
            }

            API_CODE.CONTENT_TOO_LARGE.code -> {
                syncApiErrorRepository.addError(feature, REQUEST_SIZE_LIMIT_EXCEEDED)
            }

            API_CODE.VALIDATION_ERROR.code -> {
                syncApiErrorRepository.addError(feature, VALIDATION_ERROR)
            }

            API_CODE.TOO_MANY_REQUESTS_1.code -> {
                syncApiErrorRepository.addError(feature, TOO_MANY_REQUESTS)
            }

            API_CODE.TOO_MANY_REQUESTS_2.code -> {
                syncApiErrorRepository.addError(feature, TOO_MANY_REQUESTS)
            }
        }
        syncPixels.fireDailySyncApiErrorPixel(feature, apiError)
    }
}
