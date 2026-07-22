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
import com.duckduckgo.adblocking.impl.store.AdBlockingSessionStore
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

    /**
     * Reactive equivalent of [canInject]: emits whether ad blocking is currently active (remote
     * config kill-switch on, not in contingency mode, and the user/default setting on). Unlike
     * [canInject] — whose snapshot is seeded `false` until the DataStore-backed user setting
     * resolves — collecting this waits for a real value, so it is safe for one-shot reads at app
     * start.
     */
    fun observeCanInject(): Flow<Boolean>

    /**
     * Emits where (or whether) the ad blocking entry should be shown in settings and re-emits when
     * it changes (e.g. after a remote privacy config download). This is the single source of truth
     * for the entry's placement across the settings screen.
     */
    fun settingsPlacementFlow(): Flow<SettingsPlacement>

    /**
     * Emits the current [AdBlockingState], distinguishing whether ad blocking is enabled because
     * the user turned it on ([AdBlockingState.Enabled.UserEnabled]) or because of the remote
     * default ([AdBlockingState.Enabled.Default]).
     */
    fun observeState(): Flow<AdBlockingState>

    fun currentState(): AdBlockingState
}

sealed interface AdBlockingState {
    data object Uninitialized : AdBlockingState
    sealed interface Disabled : AdBlockingState {
        data object Permanent : Disabled
        data object UntilRelaunch : Disabled
    }
    sealed interface Enabled : AdBlockingState {
        data object UserEnabled : Enabled
        data object Default : Enabled
    }
}

/**
 * Where the ad blocking entry is rendered in settings.
 */
sealed interface SettingsPlacement {
    data object Protections : SettingsPlacement
    data object Other : SettingsPlacement
    data object Hidden : SettingsPlacement
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAdBlockingStatusChecker @Inject constructor(
    private val feature: AdBlockingExtensionFeature,
    private val settingsRepository: AdBlockingSettingsRepository,
    private val sessionStore: AdBlockingSessionStore,
    @AppCoroutineScope appScope: CoroutineScope,
) : AdBlockingStatusChecker {

    private val state: StateFlow<AdBlockingState> = observeState()
        .stateIn(appScope, SharingStarted.Eagerly, AdBlockingState.Uninitialized)

    override fun canInject(): Boolean {
        if (!feature.self().isEnabled()) {
            logcat { "Kill-switch is off" }
            return false
        }
        if (feature.enableContingencyMode().isEnabled()) {
            logcat { "Contingency mode is on" }
            return false
        }
        if (state.value !is AdBlockingState.Enabled) {
            logcat { "Ad blocking disabled" }
            return false
        }
        return true
    }

    override fun observeCanInject(): Flow<Boolean> =
        combine(
            feature.self().enabled(),
            feature.enableContingencyMode().enabled(),
            observeState(),
        ) { killSwitchOn, contingencyModeOn, state ->
            killSwitchOn && !contingencyModeOn && state is AdBlockingState.Enabled
        }

    override fun settingsPlacementFlow(): Flow<SettingsPlacement> =
        combine(
            feature.self().enabled(),
            feature.adBlockingUXImprovements().enabled(),
        ) { killSwitchOn, uxImprovementsEnabled ->
            when {
                !killSwitchOn -> SettingsPlacement.Hidden
                uxImprovementsEnabled -> SettingsPlacement.Protections
                else -> SettingsPlacement.Other
            }
        }

    override fun observeState(): Flow<AdBlockingState> =
        combine(
            settingsRepository.isEnabledFlow(),
            feature.enabledByDefault().enabled(),
            sessionStore.observe(),
        ) { userSetting, enabledByDefault, disabledUntilRelaunch ->
            when {
                disabledUntilRelaunch -> AdBlockingState.Disabled.UntilRelaunch
                userSetting == true -> AdBlockingState.Enabled.UserEnabled
                userSetting == false -> AdBlockingState.Disabled.Permanent
                enabledByDefault -> AdBlockingState.Enabled.Default
                else -> AdBlockingState.Disabled.Permanent
            }
        }

    override fun currentState(): AdBlockingState = state.value
}
