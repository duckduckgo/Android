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
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.malicioussiteprotection.impl.MaliciousSiteProtectionFeature
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface MaliciousSiteProtectionRCRepository {
    fun isExempted(hostName: String): Boolean
    val exceptions: CopyOnWriteArrayList<FeatureException>
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = MaliciousSiteProtectionRCRepository::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
@SingleInstanceIn(AppScope::class)
class RealMaliciousSiteProtectionRCRepository @Inject constructor(
    private val feature: MaliciousSiteProtectionFeature,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    @IsMainProcess private val isMainProcess: Boolean,
) : MaliciousSiteProtectionRCRepository, PrivacyConfigCallbackPlugin {

    override val exceptions = CopyOnWriteArrayList<FeatureException>()

    init {
        loadToMemory()
    }

    override fun onPrivacyConfigDownloaded() {
        loadToMemory()
    }

    override fun isExempted(hostName: String): Boolean {
        return exceptions.any { it.domain == hostName }
    }

    private fun loadToMemory() {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                exceptions.clear()
                exceptions.addAll(feature.self().getExceptions())
            }
        }
    }
}
