/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.adblocking.impl.domain

import com.duckduckgo.adblocking.impl.AdBlockingSettingsRepository
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import logcat.logcat
import javax.inject.Inject

interface AdBlockingStatusChecker {
    fun canInject(): Boolean
    fun isShownInSettings(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAdBlockingStatusChecker @Inject constructor(
    private val feature: AdBlockingExtensionFeature,
    settingsRepository: AdBlockingSettingsRepository,
    @AppCoroutineScope appScope: CoroutineScope,
) : AdBlockingStatusChecker {

    private val userEnabled: StateFlow<Boolean> = settingsRepository.isEnabledFlow()
        .stateIn(appScope, SharingStarted.Eagerly, initialValue = false)

    override fun canInject(): Boolean {
        if (!feature.self().isEnabled()) {
            logcat { "Kill-switch is off" }
            return false
        }
        if (feature.enableContingencyMode().isEnabled()) {
            logcat { "Contingency mode is on" }
            return false
        }
        if (!userEnabled.value) {
            logcat { "User disabled ad blocking" }
            return false
        }
        return true
    }

    override fun isShownInSettings(): Boolean = feature.isDiscoverable().isEnabled()
}
