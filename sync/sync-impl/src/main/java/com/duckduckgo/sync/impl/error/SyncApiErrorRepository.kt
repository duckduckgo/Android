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
import com.duckduckgo.sync.api.engine.SyncFeatureType
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters
import com.duckduckgo.sync.store.dao.SyncApiErrorDao
import com.duckduckgo.sync.store.model.SyncApiError
import com.duckduckgo.sync.store.model.SyncApiErrorType
import java.util.Locale
import javax.inject.Inject

interface SyncApiErrorRepository {
    fun addError(
        feature: SyncFeatureType,
        apiError: SyncApiErrorType,
    )

    fun getErrorsByDate(date: String): List<SyncApiErrorPixelData>
}

data class SyncApiErrorPixelData(
    val name: String,
    val count: String,
)

class RealSyncApiErrorRepository @Inject constructor(private val apiErrorDao: SyncApiErrorDao) : SyncApiErrorRepository {
    override fun addError(
        feature: SyncFeatureType,
        apiError: SyncApiErrorType,
    ) {
        val today = DatabaseDateFormatter.getUtcIsoLocalDate()
        val todaysError = apiErrorDao.featureErrorByDate(feature.field, apiError.name, today)
        if (todaysError == null) {
            apiErrorDao.insert(SyncApiError(feature = feature.field, errorType = apiError, count = 1, date = today))
        } else {
            apiErrorDao.incrementCount(feature.field, apiError.name, today)
        }
    }

    override fun getErrorsByDate(date: String): List<SyncApiErrorPixelData> {
        return apiErrorDao.errorsByDate(date).map {
            val errorType = when (it.errorType) {
                SyncApiErrorType.TOO_MANY_REQUESTS -> SyncPixelParameters.TOO_MANY_REQUESTS
                SyncApiErrorType.OBJECT_LIMIT_EXCEEDED -> SyncPixelParameters.OBJECT_LIMIT_EXCEEDED_COUNT
                SyncApiErrorType.REQUEST_SIZE_LIMIT_EXCEEDED -> SyncPixelParameters.REQUEST_SIZE_LIMIT_EXCEEDED_COUNT
                SyncApiErrorType.VALIDATION_ERROR -> SyncPixelParameters.VALIDATION_ERROR_COUNT
            }
            val errorName = String.format(Locale.US, errorType, it.feature)
            SyncApiErrorPixelData(errorName, it.count.toString())
        }
    }
}
