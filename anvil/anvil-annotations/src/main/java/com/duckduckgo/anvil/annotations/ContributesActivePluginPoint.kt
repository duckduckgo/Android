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

package com.duckduckgo.anvil.annotations

import kotlin.reflect.KClass

/**
 * Anvil annotation to generate plugin points that are guarded by a remote feature flag.
 *
 * Active plugins need to extend from [ActivePlugin]
 *
 * Usage:
 * ```kotlin
 * @ContributesActivePluginPoint(SomeDaggerScope::class)
 * interface MyPlugin : ActivePlugin {
 *
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ContributesActivePluginPoint(
    /** The scope in which to include this contributed PluginPoint */
    val scope: KClass<*>,

    /**
     * The type that the plugin point will be bound to. This is useful when the plugin interfaces are defined in
     * modules where we don't want or can't generate code, eg. API gradle modules.
     *
     * usage:
     * ```kotlin
     * @ContributesActivePluginPoint(
     *   scope = AppScope::class,
     *   boundType: MyPlugin::class
     * )
     * interface MyPluginPoint : MyPlugin
     *
     * interface MyPlugin : ActivePlugin {...}
     * ```
     */
    val boundType: KClass<*> = Unit::class,
)
