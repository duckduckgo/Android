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

package com.duckduckgo.examplefeature.internal.quack

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureToggleNames
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.mehow.quack.QuackAction
import io.mehow.quack.QuackAction.Args
import io.mehow.quack.QuackAction.Outcome
import io.mehow.quack.QuackAction.Terminal
import io.mehow.quack.hints
import io.mehow.quack.required
import javax.inject.Inject

@ContributesIntoSet(AppScope::class, binding = binding<QuackAction>())
class ToggleFeatureFlagQuackAction @Inject constructor(
    names: FeatureFlagNames,
    inventory: FeatureTogglesInventory,
) : FeatureFlagQuackAction(names, inventory) {
    override val id = "ff.toggle"
    override val description = "Toggles a feature flag"

    override fun targetState(toggle: Toggle) = !toggle.isEnabled()
}

@ContributesIntoSet(AppScope::class, binding = binding<QuackAction>())
class EnableFeatureFlagQuackAction @Inject constructor(
    names: FeatureFlagNames,
    inventory: FeatureTogglesInventory,
) : FeatureFlagQuackAction(names, inventory) {
    override val id = "ff.enable"
    override val description = "Enables a feature flag"

    override fun targetState(toggle: Toggle) = true
}

@ContributesIntoSet(AppScope::class, binding = binding<QuackAction>())
class DisableFeatureFlagQuackAction @Inject constructor(
    names: FeatureFlagNames,
    inventory: FeatureTogglesInventory,
) : FeatureFlagQuackAction(names, inventory) {
    override val id = "ff.disable"
    override val description = "Disables a feature flag"

    override fun targetState(toggle: Toggle) = false
}

abstract class FeatureFlagQuackAction(
    names: FeatureFlagNames,
    private val inventory: FeatureTogglesInventory,
) : QuackAction {
    private val flagName = QuackAction.Param.string("name", "Feature flag name")
        .required()
        .hints(names.all)

    final override val params = listOf(flagName)

    abstract fun targetState(toggle: Toggle): Boolean

    final override suspend fun execute(args: Args, terminal: Terminal): Outcome {
        val name = args[flagName].trim()
        val toggle = inventory.getAll().firstOrNull { it.flagName() == name }
            ?: return Outcome.Error("No feature flag named $name")
        val enable = targetState(toggle)
        val storedState = toggle.getRawStoredState() ?: State()
        toggle.setRawStoredState(storedState.copy(remoteEnableState = enable, enable = enable))
        val isEnabled = toggle.isEnabled()
        return if (isEnabled == enable) {
            Outcome.Ok("$name is ${isEnabled.label()}")
        } else {
            Outcome.Error("$name stays ${isEnabled.label()} because of its targets, rollout, version range or internal override")
        }
    }

    private fun Boolean.label() = if (this) "enabled" else "disabled"
}

@SingleIn(AppScope::class)
class FeatureFlagNames @Inject constructor(
    names: Set<FeatureToggleNames>,
) {
    val all = names
        .flatMap { listOf(it.featureName) + it.subFeatureNames.map { subFeatureName -> "${it.featureName}.$subFeatureName" } }
        .sorted()
}

private fun Toggle.flagName(): String {
    val featureName = featureName()
    return featureName.parentName?.let { "$it.${featureName.name}" } ?: featureName.name
}
