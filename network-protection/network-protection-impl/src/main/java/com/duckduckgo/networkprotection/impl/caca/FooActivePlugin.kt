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

package com.duckduckgo.networkprotection.impl.caca

import com.duckduckgo.anvil.annotations.ContributesActivePlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.test.MyPlugin
import com.duckduckgo.test.MyPlugin.Companion.BAR_PLUGIN_PRIORITY
import com.duckduckgo.test.MyPlugin.Companion.BAZ_PLUGIN_PRIORITY
import javax.inject.Inject
import logcat.logcat

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = MyPlugin::class,
    defaultActiveValue = false,
)
class FooActivePlugin @Inject constructor() : MyPlugin {
    override fun doSomething() {
        logcat { "Aitor Foo" }
    }
}

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = MyPlugin::class,
    priority = BAR_PLUGIN_PRIORITY,
)
class BarActivePlugin @Inject constructor() : MyPlugin {
    override fun doSomething() {
        logcat { "Aitor Bar" }
    }
}

@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = MyPlugin::class,
    priority = BAZ_PLUGIN_PRIORITY,
)
class BazActivePlugin @Inject constructor() : MyPlugin {
    override fun doSomething() {
        logcat { "Aitor Baz" }
    }
}
