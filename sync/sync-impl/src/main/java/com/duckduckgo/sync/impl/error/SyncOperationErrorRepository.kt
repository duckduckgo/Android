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

import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters
import com.duckduckgo.sync.store.dao.SyncOperationErrorDao
import com.duckduckgo.sync.store.model.GENERIC_FEATURE
import com.duckduckgo.sync.store.model.SyncOperationError
import com.duckduckgo.sync.store.model.SyncOperationErrorType
import com.duckduckgo.sync.store.model.SyncOperationErrorType.DATA_DECRYPT
import com.duckduckgo.sync.store.model.SyncOperationErrorType.DATA_ENCRYPT
import com.duckduckgo.sync.store.model.SyncOperationErrorType.DATA_PERSISTER_ERROR
import com.duckduckgo.sync.store.model.SyncOperationErrorType.DATA_PROVIDER_ERROR
import com.duckduckgo.sync.store.model.SyncOperationErrorType.ORPHANS_PRESENT
import com.duckduckgo.sync.store.model.SyncOperationErrorType.TIMESTAMP_CONFLICT
import java.util.Locale
import javax.inject.Inject

interface SyncOperationErrorRepository {

    fun addError(
        feature: String,
        apiError: SyncOperationErrorType,
    )

    fun addError(
        apiError: SyncOperationErrorType,
    )

    fun getErrorsByDate(date: String): List<SyncOperationErrorPixelData>
}

data class SyncOperationErrorPixelData(
    val name: String,
    val count: String,
)

class RealSyncOperationErrorRepository @Inject constructor(private val dao: SyncOperationErrorDao) : SyncOperationErrorRepository {
    override fun addError(
        feature: String,
        apiError: SyncOperationErrorType,
    ) {
        val today = DatabaseDateFormatter.getUtcIsoLocalDate()
        val todaysError = dao.featureErrorByDate(feature, apiError.name, today)
        if (todaysError == null) {
            dao.insert(SyncOperationError(feature = feature, errorType = apiError, count = 1, date = today))
        } else {
            dao.incrementCount(feature, apiError.name, today)
        }
    }

    override fun addError(
        apiError: SyncOperationErrorType,
    ) {
        val today = DatabaseDateFormatter.getUtcIsoLocalDate()
        val todaysError = dao.featureErrorByDate(GENERIC_FEATURE, apiError.name, today)
        if (todaysError == null) {
            dao.insert(SyncOperationError(feature = GENERIC_FEATURE, errorType = apiError, count = 1, date = today))
        } else {
            dao.incrementCount(GENERIC_FEATURE, apiError.name, today)
        }
    }

    override fun getErrorsByDate(date: String): List<SyncOperationErrorPixelData> {
        return dao.errorsByDate(date).map {
            val errorType = when (it.errorType) {
                DATA_DECRYPT -> SyncPixelParameters.DATA_DECRYPT_ERROR
                DATA_ENCRYPT -> SyncPixelParameters.DATA_ENCRYPT_ERROR
                DATA_PROVIDER_ERROR -> SyncPixelParameters.DATA_PROVIDER_ERROR_PARAM
                DATA_PERSISTER_ERROR -> SyncPixelParameters.DATA_PERSISTER_ERROR_PARAM
                TIMESTAMP_CONFLICT -> SyncPixelParameters.TIMESTAMP_CONFLICT
                ORPHANS_PRESENT -> SyncPixelParameters.ORPHANS_PRESENT
            }
            val errorName = String.format(Locale.US, errorType, it.feature)
            SyncOperationErrorPixelData(errorName, it.count.toString())
        }
    }
}
