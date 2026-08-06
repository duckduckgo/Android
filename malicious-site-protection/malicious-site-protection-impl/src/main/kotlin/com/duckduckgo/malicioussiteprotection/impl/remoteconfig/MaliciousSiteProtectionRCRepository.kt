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

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.browser.Domain
import com.duckduckgo.app.browser.UriString
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.malicioussiteprotection.impl.MaliciousSiteProtectionFeature
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

interface MaliciousSiteProtectionRCRepository {
    fun isExempted(domain: Domain): Boolean
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
) : MaliciousSiteProtectionRCRepository, PrivacyConfigCallbackPlugin, MainProcessLifecycleObserver {

    @Volatile
    private var exceptionDomains = emptyList<Domain>()

    override fun onCreate(owner: LifecycleOwner) {
        loadToMemory()
    }

    override fun onPrivacyConfigDownloaded() {
        loadToMemory()
    }

    override fun isExempted(domain: Domain): Boolean {
        return exceptionDomains.any { UriString.sameOrSubdomain(domain, it) }
    }

    private fun loadToMemory() {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                exceptionDomains = feature.self().getExceptions().also {
                    logcat("Cris") { "MSP size=${it.size} domain=${it.firstOrNull()?.domain} daxDomain=${it.firstOrNull()?.daxDomain}" }
                }.map { Domain(it.domain) }
            }
        }
    }
}
