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

package com.duckduckgo.remote.messaging.store

import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.withContext
import kotlin.random.Random

interface RemoteMessagingCohortStore {
    suspend fun getPercentile(remoteMessageId: String): Float
}

class RemoteMessagingCohortStoreImpl constructor(
    database: RemoteMessagingDatabase,
    private val dispatchers: DispatcherProvider,
) : RemoteMessagingCohortStore {

    private val cohortDao: RemoteMessagingCohortDao = database.remoteMessagingCohortDao()

    override suspend fun getPercentile(remoteMessageId: String): Float {
        return withContext(dispatchers.io()) {
            val cohort = cohortDao.messageById(remoteMessageId)
            if (cohort == null) {
                val percentile = calculatePercentile()
                val remoteMessagingCohort = RemoteMessagingCohort(messageId = remoteMessageId, percentile = percentile)
                cohortDao.insert(remoteMessagingCohort)
                return@withContext percentile
            } else {
                return@withContext cohort.percentile
            }
        }
    }

    private fun calculatePercentile(): Float {
        return Random.nextDouble(1.0).toFloat()
    }
}
