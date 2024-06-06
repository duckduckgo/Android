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

package com.duckduckgo.app.anr.internal.setting

import com.duckduckgo.app.anr.internal.store.AnrInternalEntity
import com.duckduckgo.app.anr.internal.store.CrashANRsInternalDatabase
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface CrashANRsRepository {
    fun getANRs(): Flow<List<AnrInternalEntity>>
    fun insertANR(anr: AnrInternalEntity)
}

@ContributesBinding(AppScope::class)
class RealCrashANRsRepository @Inject constructor(
    private val crashANRsInternalDatabase: CrashANRsInternalDatabase,
) : CrashANRsRepository {
    override fun getANRs(): Flow<List<AnrInternalEntity>> {
        return crashANRsInternalDatabase.arnDao().getAnrs()
    }

    override fun insertANR(anr: AnrInternalEntity) {
        crashANRsInternalDatabase.arnDao().insertAnr(anr)
    }
}
