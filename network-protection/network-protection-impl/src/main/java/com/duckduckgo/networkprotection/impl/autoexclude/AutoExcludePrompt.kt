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

import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.networkprotection.impl.autoexclude.AutoExcludePrompt.Trigger
import com.duckduckgo.networkprotection.impl.autoexclude.AutoExcludePrompt.Trigger.NEW_FLAGGED_APP
import com.duckduckgo.networkprotection.store.NetPExclusionListRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AutoExcludePrompt {
    suspend fun getAppsForPrompt(trigger: Trigger): List<VpnIncompatibleApp>

    enum class Trigger {
        NEW_FLAGGED_APP,
        INCOMPATIBLE_APP_MANUALLY_EXCLUDED,
    }
}

@ContributesBinding(ActivityScope::class)
class RealAutoExcludePrompt @Inject constructor(
    private val netPExclusionListRepository: NetPExclusionListRepository,
    private val autoExcludeAppsRepository: AutoExcludeAppsRepository,
) : AutoExcludePrompt {
    override suspend fun getAppsForPrompt(trigger: Trigger): List<VpnIncompatibleApp> {
        return if (trigger == NEW_FLAGGED_APP) {
            getFlaggedAppsForPrompt().also {
                autoExcludeAppsRepository.markAppsAsShown(it)
            }
        } else {
            emptyList()
        }
    }

    private suspend fun getFlaggedAppsForPrompt(): List<VpnIncompatibleApp> {
        val manuallyExcludedApps = netPExclusionListRepository.getManualAppExclusionList().filter {
            !it.isProtected
        }.map {
            it.packageId
        }
        return autoExcludeAppsRepository.getAppsForAutoExcludePrompt().filter {
            !manuallyExcludedApps.contains(it.packageName)
        }
    }
}
