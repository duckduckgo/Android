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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import logcat.logcat
import javax.inject.Inject

interface AdBlockingStatusChecker {
    fun canInject(): Boolean
    fun isShownInSettings(): Boolean

    /**
     * Emits whether the feature is shown in settings and re-emits when it changes
     * (e.g. after a remote privacy config download).
     */
    fun isShownInSettingsFlow(): Flow<Boolean>

    /**
     * Emits the current [AdBlockingState], distinguishing whether ad blocking is enabled because
     * the user turned it on ([AdBlockingState.Enabled.UserEnabled]) or because of the remote
     * default ([AdBlockingState.Enabled.Default]).
     */
    fun observeState(): Flow<AdBlockingState>

    fun currentState(): AdBlockingState
}

sealed interface AdBlockingState {
    data object Disabled : AdBlockingState
    sealed interface Enabled : AdBlockingState {
        data object UserEnabled : Enabled
        data object Default : Enabled
    }
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAdBlockingStatusChecker @Inject constructor(
    private val feature: AdBlockingExtensionFeature,
    private val settingsRepository: AdBlockingSettingsRepository,
    @AppCoroutineScope appScope: CoroutineScope,
) : AdBlockingStatusChecker {

    private val userEnabled: StateFlow<Boolean> = combine(
        settingsRepository.isEnabledFlow(),
        feature.enabledByDefault().enabled(),
    ) { stored, enabledByDefault ->
        stored ?: enabledByDefault
    }.stateIn(appScope, SharingStarted.Eagerly, initialValue = false)

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

    override fun isShownInSettings(): Boolean = feature.self().isEnabled()

    override fun isShownInSettingsFlow(): Flow<Boolean> = feature.self().enabled()

    override fun observeState(): Flow<AdBlockingState> =
        combine(
            settingsRepository.isEnabledFlow(),
            feature.enabledByDefault().enabled(),
        ) { userSetting, enabledByDefault ->
            when (userSetting) {
                true -> AdBlockingState.Enabled.UserEnabled
                false -> AdBlockingState.Disabled
                null -> if (enabledByDefault) {
                    AdBlockingState.Enabled.Default
                } else {
                    AdBlockingState.Disabled
                }
            }
        }
    private val isUserEnabled: StateFlow<AdBlockingState> = observeState()
        .stateIn(appScope, SharingStarted.Eagerly, AdBlockingState.Disabled)

    override fun currentState(): AdBlockingState = isUserEnabled.value
}
