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

package com.duckduckgo.featuretoggles.internal.testseeder

import android.annotation.SuppressLint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.testseeder.api.TestSeederKey
import com.duckduckgo.testseeder.api.TestSeederPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class FeatureFlagSeederPlugin @Inject constructor(
    private val inventory: FeatureTogglesInventory,
) : TestSeederPlugin {

    override val handledKeys = setOf(TestSeederKey.FEATURE_FLAGS.key)

    @SuppressLint("DenyListedApi")
    override suspend fun apply(key: String, value: String) {
        if (value.isBlank()) return
        val toggles = inventory.getAll()
        value.split(';').forEach { rawAssignment ->
            val assignment = rawAssignment.trim()
            val parts = assignment.split('=')
            check(parts.size == 2 && parts[0].isNotBlank()) { "Malformed featureFlags assignment: '$assignment'" }
            val (address, stateString) = parts
            val toggle = toggles.find { it.featureName().address() == address }
                ?: error("Unknown feature flag '$address'. Use 'feature' or 'feature.subFeature' names.")
            val state = when (stateString) {
                "true" -> true
                "false" -> false
                // With no stored state the effective state is the build default, so the flip is
                // predictable. Stored state means something wrote it first (config processing, or an
                // earlier seed), and flipping an unknown starting point might silently run the arm
                // the caller didn't ask for.
                "invert" -> {
                    check(toggle.getRawStoredState() == null) {
                        "Cannot invert '$address': it already has stored state. Clear state before running or use an explicit true/false assignment."
                    }
                    !toggle.isEnabled()
                }
                else -> error("Invalid state '$stateString' in featureFlags assignment: '$assignment'")
            }
            toggle.setRawStoredState(Toggle.State(enable = state))
        }
    }

    private fun Toggle.FeatureName.address(): String = parentName?.let { "$it.$name" } ?: name
}
