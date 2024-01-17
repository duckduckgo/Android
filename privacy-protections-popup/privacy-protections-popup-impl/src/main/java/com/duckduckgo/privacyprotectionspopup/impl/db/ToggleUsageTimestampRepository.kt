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

package com.duckduckgo.privacyprotectionspopup.impl.db

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ToggleUsageTimestampRepository {
    fun getToggleUsageTimestamp(): Flow<Instant?>
    suspend fun setToggleUsageTimestamp(timestamp: Instant)
}

@ContributesBinding(AppScope::class)
class ToggleUsageTimestampRepositoryImpl @Inject constructor(
    private val dao: ToggleUsageTimestampDao,
) : ToggleUsageTimestampRepository {

    override fun getToggleUsageTimestamp(): Flow<Instant?> =
        dao.query().map { it?.timestamp }

    override suspend fun setToggleUsageTimestamp(timestamp: Instant) {
        dao.insert(ToggleUsageTimestamp(timestamp = timestamp))
    }
}
