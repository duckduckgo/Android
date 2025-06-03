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
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.store.model.SyncOperationErrorType
import com.duckduckgo.sync.store.model.SyncOperationErrorType.TIMESTAMP_CONFLICT
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.logcat

interface SyncOperationErrorRecorder {

    fun record(
        errorType: SyncOperationErrorType,
    )

    fun record(
        feature: String,
        errorType: SyncOperationErrorType,
    )
}

@ContributesBinding(AppScope::class)
class RealSyncOperationErrorRecorder @Inject constructor(
    private val syncPixels: SyncPixels,
    private val repository: SyncOperationErrorRepository,
) : SyncOperationErrorRecorder {
    override fun record(
        errorType: SyncOperationErrorType,
    ) {
        logcat { "Sync-Error: Recording Operation Error $errorType" }
        repository.addError(errorType)
    }

    override fun record(
        feature: String,
        errorType: SyncOperationErrorType,
    ) {
        logcat { "Sync-Error: Recording Operation Error $errorType for $feature" }
        if (errorType == TIMESTAMP_CONFLICT) {
            syncPixels.fireTimestampConflictPixel(feature)
        }

        repository.addError(feature, errorType)
    }
}
