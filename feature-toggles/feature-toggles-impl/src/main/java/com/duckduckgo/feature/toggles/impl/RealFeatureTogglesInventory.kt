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

package com.duckduckgo.feature.toggles.impl

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealFeatureTogglesInventory @Inject constructor(
    private val toggles: DaggerSet<FeatureTogglesInventory>,
    private val dispatcherProvider: DispatcherProvider,
) : FeatureTogglesInventory {
    override suspend fun getAll(): List<Toggle> = withContext(dispatcherProvider.io()) {
        return@withContext toggles.flatMap { it.getAll() }.distinctBy { it.featureName() }
    }

    override suspend fun getAllTogglesForParent(name: String): List<Toggle> = withContext(dispatcherProvider.io()) {
        return@withContext getAll().filter { it.featureName().parentName == name }
    }

    override suspend fun getAllActiveExperimentToggles(): List<Toggle> = withContext(dispatcherProvider.io()) {
        return@withContext getAll().filter { it.getCohort() != null && it.isEnabled() }
    }
}
