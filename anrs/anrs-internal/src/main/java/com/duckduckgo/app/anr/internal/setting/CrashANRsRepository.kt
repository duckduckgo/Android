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
import com.duckduckgo.app.anr.internal.store.CrashInternalEntity
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface CrashANRsRepository {
    fun getANRs(): Flow<List<AnrInternalEntity>>
    fun getCrashes(): Flow<List<CrashInternalEntity>>
    suspend fun insertANR(anr: AnrInternalEntity)
    suspend fun insertCrash(crash: CrashInternalEntity)
}

@ContributesBinding(AppScope::class)
class RealCrashANRsRepository @Inject constructor(
    private val crashANRsInternalDatabase: CrashANRsInternalDatabase,
    private val dispatcherProvider: DispatcherProvider,
) : CrashANRsRepository {
    override fun getANRs(): Flow<List<AnrInternalEntity>> {
        return crashANRsInternalDatabase.anrDao().getAnrs()
    }

    override fun getCrashes(): Flow<List<CrashInternalEntity>> {
        return crashANRsInternalDatabase.crashDao().getCrashes()
    }

    override suspend fun insertANR(anr: AnrInternalEntity) {
        withContext(dispatcherProvider.io()) {
            crashANRsInternalDatabase.anrDao().insertAnr(anr)
        }
    }

    override suspend fun insertCrash(crash: CrashInternalEntity) {
        return withContext(dispatcherProvider.io()) {
            crashANRsInternalDatabase.crashDao().insertCrash(crash)
        }
    }
}
