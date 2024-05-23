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
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.AppScope
import javax.inject.Inject

@ContributesActivePluginPoint(
    scope = AppScope::class,
)
interface MyPlugin : ActivePluginPoint.ActivePlugin {
    fun doSomething()
}

interface TriggeredMyPlugin : ActivePluginPoint.ActivePlugin {
    fun doSomething()
}

@ContributesActivePluginPoint(
    scope = AppScope::class,
    boundType = TriggeredMyPlugin::class,
)
private interface TriggeredMyPluginTrigger

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = TriggeredMyPlugin::class,
    defaultActiveValue = false,
)
class FooActiveTriggeredMyPlugin @Inject constructor() : TriggeredMyPlugin {
    override fun doSomething() {
    }
}

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = MyPlugin::class,
    defaultActiveValue = false,
)
class FooActivePlugin @Inject constructor() : MyPlugin {
    override fun doSomething() {
    }
}

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = MyPlugin::class,
    priority = 100,
)
class BarActivePlugin @Inject constructor() : MyPlugin {
    override fun doSomething() {
    }
}

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = MyPlugin::class,
    priority = 50,
)
class BazActivePlugin @Inject constructor() : MyPlugin {
    override fun doSomething() {
    }
}
