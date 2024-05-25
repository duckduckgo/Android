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

package com.duckduckgo.common.utils.plugins

@Deprecated(
    message = "Use \"ActivePluginPoint\" instead to ensure \"JvmSuppressWildcards\" is not forgotten",
    replaceWith = ReplaceWith("ActivePluginPoint"),
)
interface InternalActivePluginPoint<out T : @JvmSuppressWildcards ActivePlugin> {
    /** @return the list of plugins of type <T> */
    suspend fun getPlugins(): Collection<T>
}

/**
 * Active plugins SHALL extend from [ActivePlugin]
 *
 * Usage:
 * ```kotlin
 * @ContributesActivePluginPoint(
 *     scope = SomeScope::class,
 * )
 * interface MyActivePlugin : ActivePlugin {...}
 *
 * @ContributesActivePlugin(
 *     scope = SomeScope::class,
 *     boundType = MyActivePlugin::class,
 * )
 * class FooMyActivePlugin @Inject constructor() : MyActivePlugin {...}
 * ```
 */
interface ActivePlugin {
    suspend fun isActive(): Boolean = true
}

/**
 * Use this typealias to collect your [ActivePlugin]s
 *
 * Usage:
 * ```kotlin
 * class MyClass @Inject constructor(
 *   private val pp: ActivePluginPoint<MyActivePlugin>,
 * ) {...}
 * ```
 */
typealias ActivePluginPoint<T> = InternalActivePluginPoint<@JvmSuppressWildcards T>
