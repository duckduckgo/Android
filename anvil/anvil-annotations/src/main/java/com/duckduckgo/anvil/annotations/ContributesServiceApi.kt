/*
 * Copyright (c) 2022 DuckDuckGo
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
 * Anvil annotation to generate a Retrofit service interface implementation.
 *
 * Usage:
 * ```kotlin
 * @ContributesServiceApi(SomeDaggerScope::class)
 * interface MyServiceApi {
 *
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ContributesServiceApi(
    /** The scope in which to include this contributed PluginPoint */
    val scope: KClass<*>,

    /**
     * The type that the plugin point will be bound to. This is useful when the plugin interfaces are defined in
     * modules where we don't want or can't generate code, eg. API gradle modules.
     *
     * usage:
     * ```kotlin
     * @ContributesServiceApi(
     *   scope = AppScope::class,
     * )
     * interface MyRetrofitServiceApi {...}
     * ```
     */
    val boundType: KClass<*> = Unit::class,
)
