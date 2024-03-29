/*
 * Copyright (c) 2023 DuckDuckGo
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
 * Anvil annotation to generate and contribute the Map<ActivityParams, Class<ActivityParams>> to the activity starter
 *
 * Usage:
 * ```kotlin
 * @ContributeToActivityStarter(ExampleActivityParams::class)
 * class MyActivity {
 *
 * }
 *
 * data class ExampleActivityParams(...) : ActivityParams
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class ContributeToActivityStarter(
    /** The type of the input parameters received by the Activity */
    val paramsType: KClass<*>,
    /** Declares the deeplink name for the Activity */
    val screenName: String = "",
)
