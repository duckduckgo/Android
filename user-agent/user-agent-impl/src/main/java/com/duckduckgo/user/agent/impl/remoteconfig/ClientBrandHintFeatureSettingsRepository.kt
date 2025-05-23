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

package com.duckduckgo.user.agent.impl.remoteconfig

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.user.agent.impl.store.ClientBrandHintDatabase
import com.duckduckgo.user.agent.impl.store.ClientHintBrandDomainEntity
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.INFO
import logcat.logcat

interface ClientBrandHintFeatureSettingsRepository {
    fun updateAllSettings(settings: ClientBrandHintSettings)
    val clientBrandHints: CopyOnWriteArrayList<ClientBrandHintDomain>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealClientBrandHintFeatureSettingsRepository @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    database: ClientBrandHintDatabase,
    @IsMainProcess isMainProcess: Boolean,
) : ClientBrandHintFeatureSettingsRepository {

    private val dao = database.clientBrandHintDao()

    override val clientBrandHints = CopyOnWriteArrayList<ClientBrandHintDomain>()

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun updateAllSettings(settings: ClientBrandHintSettings) {
        logcat(INFO) { "ClientBrandHintProvider: update domains to ${settings.domains}" }
        dao.updateAllDomains(settings.domains.map { ClientHintBrandDomainEntity(it.domain, it.brand.name) })
        loadToMemory()
    }

    private fun loadToMemory() {
        clientBrandHints.clear()
        val clientBrandHintsDomainList = dao.getAllDomains()
        logcat(INFO) { "ClientBrandHintProvider: loading domains to memory $clientBrandHintsDomainList" }
        clientBrandHints.addAll(clientBrandHintsDomainList.map { ClientBrandHintDomain(it.url, ClientBrandsHints.from(it.brand)) })
    }
}
