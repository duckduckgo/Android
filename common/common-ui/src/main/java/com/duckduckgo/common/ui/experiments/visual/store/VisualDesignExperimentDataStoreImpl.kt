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

package com.duckduckgo.common.ui.experiments.visual.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.experiments.visual.ExperimentalUIThemingFeature
import com.duckduckgo.common.ui.experiments.visual.store.VisualDesignExperimentDataStore.FeatureState
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@ContributesBinding(
    scope = AppScope::class,
    boundType = VisualDesignExperimentDataStore::class,
)
@ContributesMultibinding(scope = AppScope::class, boundType = PrivacyConfigCallbackPlugin::class)
class VisualDesignExperimentDataStoreImpl @Inject constructor(
    @VisualDesignExperiment private val store: DataStore<Preferences>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    experimentalUIThemingFeature: ExperimentalUIThemingFeature,
) : VisualDesignExperimentDataStore, PrivacyConfigCallbackPlugin {
    private companion object {

        private const val KEY_EXPERIMENT_ENABLED = "KEY_EXPERIMENT_ENABLED"
        private const val DEFAULT_EXPERIMENT_USER_PREFERENCE = true

        private const val KEY_NAVIGATION_BAR_ENABLED = "KEY_NAVIGATION_BAR_ENABLED"
        private const val DEFAULT_NAVIGATION_BAR_USER_PREFERENCE = true

        private val NO_PARENT_FEATURE = flowOf(FeatureState(isAvailable = true, isEnabled = true))
    }

    private val _experimentState = ExperimentFeature(
        store = store,
        coroutineScope = appCoroutineScope,
        targetToggle = experimentalUIThemingFeature.self(),
        prefsKey = KEY_EXPERIMENT_ENABLED,
        prefsDefault = DEFAULT_EXPERIMENT_USER_PREFERENCE,
    )
    override val experimentState: StateFlow<FeatureState> = _experimentState.state

    private val _navigationBarState = ExperimentFeature(
        store = store,
        coroutineScope = appCoroutineScope,
        parentStateFlow = experimentState,
        targetToggle = experimentalUIThemingFeature.browserNavigationBar(),
        prefsKey = KEY_NAVIGATION_BAR_ENABLED,
        prefsDefault = DEFAULT_NAVIGATION_BAR_USER_PREFERENCE,
    )
    override val navigationBarState: StateFlow<FeatureState> = _navigationBarState.state

    init {
        updateFeatureState()
    }

    override fun onPrivacyConfigDownloaded() {
        updateFeatureState()
    }

    override fun setExperimentStateUserPreference(enabled: Boolean) {
        _experimentState.setUserPreference(enabled)
    }

    override fun setNavigationBarStateUserPreference(enabled: Boolean) {
        _navigationBarState.setUserPreference(enabled)
    }

    private fun updateFeatureState() {
        _experimentState.updateFeatureState()
        _navigationBarState.updateFeatureState()
    }

    private class ExperimentFeature(
        private val store: DataStore<Preferences>,
        private val coroutineScope: CoroutineScope,
        parentStateFlow: Flow<FeatureState> = NO_PARENT_FEATURE,
        private val targetToggle: Toggle,
        private val prefsKey: String,
        private val prefsDefault: Boolean,
    ) {
        private val _available = MutableStateFlow(targetToggle.isEnabled())
        private val _enabled = store.data.map { preferences ->
            preferences[booleanPreferencesKey(prefsKey)] ?: prefsDefault
        }

        /**
         * Returns [FeatureState] of this feature.
         *
         * If the designated parent feature is disabled, both [FeatureState.isAvailable] and [FeatureState.isEnabled] will report `false`,
         * until the parent feature gets enabled.
         */
        val state: StateFlow<FeatureState> = combine(
            _available,
            _enabled,
            parentStateFlow,
        ) { available, userPrefEnabled, parentState ->
            FeatureState(
                isAvailable = parentState.isEnabled && available,
                isEnabled = parentState.isEnabled && available && userPrefEnabled,
            )
        }.stateIn(
            coroutineScope,
            SharingStarted.Eagerly,
            initialValue = FeatureState(
                isAvailable = _available.value,
                // FIXME Synchronously fetching the initial preference value is wrong but it's a temporary workaround
                //  because the omnibar implementation that relies on this value can't recreate itself dynamically,
                //  it requires the whole activity to be recreated. To avoid that recreation right after initial launch,
                //  we need the initial value to actually use the valid persisted state.
                isEnabled = _available.value && runBlocking { _enabled.first() },
            ),
        )

        /**
         * Loads the state from the [ExperimentFeature.targetToggle]'s configuration.
         */
        fun updateFeatureState() {
            coroutineScope.launch {
                _available.value = targetToggle.isEnabled()
            }
        }

        /**
         * Persists user preference.
         *
         * The preference is only considered if the parent feature is enabled and the target feature is available (turned on in the config).
         */
        fun setUserPreference(enabled: Boolean) {
            coroutineScope.launch {
                store.edit { preferences ->
                    preferences[booleanPreferencesKey(prefsKey)] = enabled
                }
            }
        }
    }
}
