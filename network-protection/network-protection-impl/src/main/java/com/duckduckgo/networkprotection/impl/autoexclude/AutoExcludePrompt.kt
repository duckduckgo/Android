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

package com.duckduckgo.networkprotection.impl.autoexclude

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.networkprotection.impl.autoexclude.AutoExcludePrompt.Trigger
import com.duckduckgo.networkprotection.impl.autoexclude.AutoExcludePrompt.Trigger.NEW_INCOMPATIBLE_APP_FOUND
import com.duckduckgo.networkprotection.store.NetPManualExclusionListRepository
import com.duckduckgo.networkprotection.store.db.VpnIncompatibleApp
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface AutoExcludePrompt {
    /**
     * Returns a list of apps to be shown in prompt according to the Trigger specified.
     *
     * This method is internally dispatched to be executed in IO.
     */
    suspend fun getAppsForPrompt(trigger: Trigger): List<VpnIncompatibleApp>

    enum class Trigger {
        NEW_INCOMPATIBLE_APP_FOUND,
        INCOMPATIBLE_APP_MANUALLY_EXCLUDED,
    }
}

@ContributesBinding(ActivityScope::class)
class RealAutoExcludePrompt @Inject constructor(
    private val netPManualExclusionListRepository: NetPManualExclusionListRepository,
    private val autoExcludeAppsRepository: AutoExcludeAppsRepository,
    private val dispatcherProvider: DispatcherProvider,
) : AutoExcludePrompt {
    override suspend fun getAppsForPrompt(trigger: Trigger): List<VpnIncompatibleApp> {
        return withContext(dispatcherProvider.io()) {
            val manuallyExcludedApps = netPManualExclusionListRepository.getManualAppExclusionList().filter {
                !it.isProtected
            }.map {
                it.packageId
            }

            if (trigger == NEW_INCOMPATIBLE_APP_FOUND) {
                getFlaggedAppsForPrompt().also {
                    autoExcludeAppsRepository.markAppsAsShown(it)
                }
            } else {
                getInstalledProtectedIncompatibleApps()
            }.filter {
                !manuallyExcludedApps.contains(it.packageName)
            }
        }
    }

    private suspend fun getFlaggedAppsForPrompt(): List<VpnIncompatibleApp> {
        return autoExcludeAppsRepository.getAppsForAutoExcludePrompt()
    }

    private suspend fun getInstalledProtectedIncompatibleApps(): List<VpnIncompatibleApp> {
        return autoExcludeAppsRepository.getInstalledIncompatibleApps()
    }
}
