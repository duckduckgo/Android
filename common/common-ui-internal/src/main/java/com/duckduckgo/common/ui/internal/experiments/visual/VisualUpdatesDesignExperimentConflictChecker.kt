/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.common.ui.internal.experiments.visual

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface VisualUpdatesDesignExperimentConflictChecker {
    /**
     * State flow which returns `true` if there are any conflicting experiments detected.
     */
    val anyConflictingExperimentEnabled: StateFlow<Boolean>
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = VisualUpdatesDesignExperimentConflictChecker::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PrivacyConfigCallbackPlugin::class,
)
@SingleInstanceIn(scope = AppScope::class)
class RealVisualDesignExperimentConflictChecker @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val featureTogglesInventory: FeatureTogglesInventory,
) : VisualUpdatesDesignExperimentConflictChecker, PrivacyConfigCallbackPlugin {

    private companion object {
        private val conflictingExperimentsNames = listOf(
            "senseOfProtectionNewUserExperimentApr25",
            "senseOfProtectionExistingUserExperimentApr25",
            "senseOfProtectionNewUserExperimentMay25",
            "senseOfProtectionExistingUserExperimentMay25",
            "senseOfProtectionNewUserExperiment27May25",
            "senseOfProtectionExistingUserExperiment27May25",
            "defaultBrowserAdditionalPrompts202501",
        )
    }

    private val _anyConflictingExperimentEnabled = MutableStateFlow(false)
    override val anyConflictingExperimentEnabled = _anyConflictingExperimentEnabled.asStateFlow()

    init {
        appCoroutineScope.launch {
            _anyConflictingExperimentEnabled.value = isAnyConflictingExperimentEnabled()
        }
    }

    private suspend fun isAnyConflictingExperimentEnabled(): Boolean {
        val activeExperimentsNames = featureTogglesInventory.getAllActiveExperimentToggles().map { it.featureName().name }
        return conflictingExperimentsNames.any { activeExperimentsNames.contains(it) }
    }

    override fun onPrivacyConfigDownloaded() {
        appCoroutineScope.launch {
            _anyConflictingExperimentEnabled.value = isAnyConflictingExperimentEnabled()
        }
    }
}
