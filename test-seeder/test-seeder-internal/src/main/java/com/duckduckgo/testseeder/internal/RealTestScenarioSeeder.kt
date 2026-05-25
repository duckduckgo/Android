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

package com.duckduckgo.testseeder.internal

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.DaggerSet
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.testseeder.api.TestScenarioSeeder
import com.duckduckgo.testseeder.api.TestSeederKey
import com.duckduckgo.testseeder.api.TestSeederPlugin
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealTestScenarioSeeder @Inject constructor(
    plugins: DaggerSet<TestSeederPlugin>,
    private val dispatchers: DispatcherProvider,
) : TestScenarioSeeder {

    private val pluginsByKey: Map<String, TestSeederPlugin> = buildMap {
        val declaredKeys = TestSeederKey.entries.map { it.key }.toSet()
        plugins.forEach { plugin ->
            plugin.handledKeys.forEach { key ->
                check(key in declaredKeys) {
                    "${plugin::class.simpleName} claims undeclared key '$key'. Add it to TestSeederKey."
                }
                val previous = put(key, plugin)
                check(previous == null) {
                    "Key collision on '$key': ${previous!!::class.simpleName} and ${plugin::class.simpleName}"
                }
            }
        }
    }

    override suspend fun seedIfNeeded(extras: Map<String, String>) {
        if (extras[TestSeederKey.IS_MAESTRO.key] != "true") return

        withContext(dispatchers.io()) {
            extras
                .filterKeys { it != TestSeederKey.IS_MAESTRO.key }
                .toSortedMap()
                .forEach { (key, value) ->
                    val plugin = pluginsByKey[key]
                        ?: error("No TestSeederPlugin handles key '$key'. Maestro typo or missing plugin?")
                    plugin.apply(key, value)
                }
        }
    }
}
