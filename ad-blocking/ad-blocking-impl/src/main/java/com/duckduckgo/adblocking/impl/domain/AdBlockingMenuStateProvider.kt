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

import android.net.Uri
import com.duckduckgo.adblocking.impl.AdBlockingExtensionDomainMatcher
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * State of the YouTube ad-blocking browser-menu item for a given page. [Hidden] when the item should not
 * appear at all; [Enabled]/[Disabled] reflect the effective blocking state so the item can label itself
 * "Disable…"/"Enable…" respectively.
 */
sealed interface AdBlockingMenuState {
    data object Hidden : AdBlockingMenuState
    data object Enabled : AdBlockingMenuState
    data object Disabled : AdBlockingMenuState
}

interface AdBlockingMenuStateProvider {
    fun observe(url: Uri): Flow<AdBlockingMenuState>
}

@ContributesBinding(AppScope::class)
class RealAdBlockingMenuStateProvider @Inject constructor(
    private val feature: AdBlockingExtensionFeature,
    private val statusChecker: AdBlockingStatusChecker,
    private val domainMatcher: AdBlockingExtensionDomainMatcher,
) : AdBlockingMenuStateProvider {

    override fun observe(url: Uri): Flow<AdBlockingMenuState> {
        if (!domainMatcher.matches(url)) return flowOf(AdBlockingMenuState.Hidden)
        return combine(
            feature.self().enabled(),
            feature.adBlockingUXImprovements().enabled(),
            feature.enableContingencyMode().enabled(),
            statusChecker.observeState(),
        ) { killSwitchOn, phase2On, contingencyOn, state ->
            when {
                !killSwitchOn || !phase2On || contingencyOn -> AdBlockingMenuState.Hidden
                state is AdBlockingState.Enabled -> AdBlockingMenuState.Enabled
                else -> AdBlockingMenuState.Disabled
            }
        }
    }
}
