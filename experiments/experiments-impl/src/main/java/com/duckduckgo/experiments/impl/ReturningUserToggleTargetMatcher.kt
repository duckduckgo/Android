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

package com.duckduckgo.experiments.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.experiments.api.VariantManager
import com.duckduckgo.experiments.impl.reinstalls.REINSTALL_VARIANT
import com.duckduckgo.feature.toggles.api.Toggle.State.Target
import com.duckduckgo.feature.toggles.api.Toggle.TargetMatcherPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class ReturningUserToggleTargetMatcher @Inject constructor(
    private val variantManager: VariantManager,
) : TargetMatcherPlugin {
    override fun matchesTargetProperty(target: Target): Boolean {
        return target.isReturningUser?.let { isReturningUserTarget ->
            val isReturningUser = variantManager.getVariantKey() == REINSTALL_VARIANT
            isReturningUserTarget == isReturningUser
        } ?: true
    }
}
