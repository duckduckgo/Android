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

package com.duckduckgo.feature.toggles.codegen

import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.anvil.annotations.ContributesActivePluginPoint
import com.duckduckgo.common.utils.plugins.ActivePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import javax.inject.Inject

@ContributesActivePluginPoint(
    scope = AppScope::class,
    featureName = "pluginPointMyPlugin",
)
interface MyPlugin : ActivePlugin {
    fun doSomething()
}

interface TriggeredMyPlugin : ActivePlugin {
    fun doSomething()
}

@ContributesActivePluginPoint(
    scope = AppScope::class,
    boundType = TriggeredMyPlugin::class,
    featureName = "pluginPointTriggeredMyPlugin",
)
private interface TriggeredMyPluginTrigger

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = TriggeredMyPlugin::class,
    defaultActiveValue = DefaultFeatureValue.FALSE,
    featureName = "pluginFooActiveTriggeredMyPlugin",
    parentFeatureName = "pluginPointTriggeredMyPlugin",
)
class FooActiveTriggeredMyPlugin @Inject constructor() : TriggeredMyPlugin {
    override fun doSomething() {
    }
}

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = MyPlugin::class,
    defaultActiveValue = DefaultFeatureValue.FALSE,
    featureName = "pluginFooActivePlugin",
    parentFeatureName = "pluginPointMyPlugin",
)
class FooActivePlugin @Inject constructor() : MyPlugin {
    override fun doSomething() {
    }
}

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = MyPlugin::class,
    defaultActiveValue = DefaultFeatureValue.INTERNAL,
    featureName = "pluginFooActiveInternalPlugin",
    parentFeatureName = "pluginPointMyPlugin",
)
class FooActiveInternalPlugin @Inject constructor() : MyPlugin {
    override fun doSomething() {
    }
}

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = MyPlugin::class,
    priority = 1000,
    featureName = "pluginBarActivePlugin",
    parentFeatureName = "pluginPointMyPlugin",
)
class BarActivePlugin @Inject constructor() : MyPlugin {
    override fun doSomething() {
    }
}

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = MyPlugin::class,
    priority = 50,
    featureName = "pluginBazActivePlugin",
    parentFeatureName = "pluginPointMyPlugin",
)
class BazActivePlugin @Inject constructor() : MyPlugin {
    override fun doSomething() {
    }
}

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = MyPlugin::class,
    priority = 50,
    supportExperiments = true,
    featureName = "pluginExperimentActivePlugin",
    parentFeatureName = "pluginPointMyPlugin",
)
class ExperimentActivePlugin @Inject constructor() : MyPlugin {
    override fun doSomething() {
    }
}

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = MyPlugin::class,
    priority = 50,
    internalAlwaysEnabled = true,
    supportExperiments = false,
    featureName = "pluginInternalAlwaysEnabledActivePlugin",
    parentFeatureName = "pluginPointMyPlugin",
)
class InternalAlwaysEnabledActivePlugin @Inject constructor() : MyPlugin {
    override fun doSomething() {
    }
}

// Fixtures for explicit featureName / parentFeatureName tests

@ContributesActivePluginPoint(
    scope = AppScope::class,
    featureName = "pluginPointExplicit",
)
interface ExplicitNamePlugin : ActivePlugin {
    fun doSomething()
}

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = ExplicitNamePlugin::class,
    featureName = "pluginExplicit",
    parentFeatureName = "pluginPointExplicit",
)
class ExplicitNameActivePlugin @Inject constructor() : ExplicitNamePlugin {
    override fun doSomething() {}
}
