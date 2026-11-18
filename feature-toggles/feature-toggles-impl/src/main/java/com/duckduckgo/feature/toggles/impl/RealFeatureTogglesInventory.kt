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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RealFeatureTogglesInventory @Inject constructor(
    private val toggles: Set<FeatureTogglesInventory>,
    private val dispatcherProvider: DispatcherProvider,
) : FeatureTogglesInventory {
    // The toggles each inventory lists are fixed at compile time and read their state live, so the list only needs building once.
    // Building it calls every toggle method through each feature's reflective proxy, and several startup paths ask for it at once.
    private val allTogglesMutex = Mutex()

    @Volatile
    private var allToggles: List<Toggle>? = null

    override suspend fun getAll(): List<Toggle> = allToggles ?: withContext(dispatcherProvider.io()) {
        allTogglesMutex.withLock {
            allToggles ?: toggles.flatMap { it.getAll() }.distinctBy { it.featureName() }.also { allToggles = it }
        }
    }

    override suspend fun getAllTogglesForParent(name: String): List<Toggle> = withContext(dispatcherProvider.io()) {
        return@withContext getAll().filter { it.featureName().parentName == name }
    }

    override suspend fun getAllActiveExperimentToggles(): List<Toggle> = withContext(dispatcherProvider.io()) {
        return@withContext getAll().filter { it.getCohort() != null && it.isEnabled() }
    }
}
