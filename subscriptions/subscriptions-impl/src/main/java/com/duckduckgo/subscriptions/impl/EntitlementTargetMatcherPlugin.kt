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

package com.duckduckgo.subscriptions.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class EntitlementTargetMatcherPlugin @Inject constructor(
    private val subscriptions: Subscriptions,
) : Toggle.TargetMatcherPlugin {
    override fun matchesTargetProperty(target: Toggle.State.Target): Boolean {
        return target.entitlement?.let { entitlement ->
            runBlocking {
                subscriptions.getEntitlementStatus().firstOrNull()?.any { it.value.lowercase() == entitlement.lowercase() } ?: false
            }
        } ?: true
    }
}
