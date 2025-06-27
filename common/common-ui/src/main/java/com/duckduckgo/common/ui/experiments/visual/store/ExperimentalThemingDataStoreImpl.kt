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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.experiments.visual.ExperimentalThemingFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

@ContributesBinding(
    scope = AppScope::class,
    boundType = ExperimentalThemingDataStore::class,
)
@SingleInstanceIn(scope = AppScope::class)
class ExperimentalThemingDataStoreImpl @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val experimentalThemingFeature: ExperimentalThemingFeature,
) : ExperimentalThemingDataStore {

    private val _singleOmnibarFeatureFlagEnabled =
        MutableStateFlow(experimentalThemingFeature.singleOmnibarFeature().isEnabled())

    override val isSingleOmnibarEnabled: StateFlow<Boolean> = _singleOmnibarFeatureFlagEnabled.stateIn(
        scope = appCoroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = _singleOmnibarFeatureFlagEnabled.value,
    )

    override suspend fun countSingleOmnibarUser() {
        withContext(dispatcherProvider.io()) {
            experimentalThemingFeature.singleOmnibarFeature().enroll()
        }
    }
}
