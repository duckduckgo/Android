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

package com.duckduckgo.malicioussiteprotection.impl.remoteconfig

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureExceptions.FeatureException
import com.duckduckgo.malicioussiteprotection.impl.data.db.FeatureExceptionEntity
import com.duckduckgo.malicioussiteprotection.impl.data.db.MaliciousSiteDao
import com.squareup.anvil.annotations.ContributesBinding
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface MaliciousSiteProtectionRCRepository {
    fun insertAllExceptions(exceptions: List<FeatureException>)
    fun isExempted(hostName: String): Boolean
    val exceptions: CopyOnWriteArrayList<FeatureException>
}

@ContributesBinding(AppScope::class)
class RealMaliciousSiteProtectionRCRepository @Inject constructor(
    @AppCoroutineScope coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    val dao: MaliciousSiteDao,
    @IsMainProcess isMainProcess: Boolean,
) : MaliciousSiteProtectionRCRepository {

    override val exceptions = CopyOnWriteArrayList<FeatureException>()

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun insertAllExceptions(exceptions: List<FeatureException>) {
        dao.updateAllExceptions(exceptions.map { FeatureExceptionEntity(domain = it.domain, reason = it.reason) })
        loadToMemory()
    }

    override fun isExempted(hostName: String): Boolean {
        return exceptions.any { it.domain == hostName }
    }

    private fun loadToMemory() {
        exceptions.clear()
        val exceptionsEntityList = dao.getExceptions()
        exceptions.addAll(exceptionsEntityList.map { FeatureException(domain = it.domain, reason = it.reason) })
    }
}
