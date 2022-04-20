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
 * Anvil annotation to generate the Dagger subcomponents for all Android types like Activities, Fragments, Services, etc.
 *
 * Usage:
 * ```kotlin
 * @ContributesSubComponent(SomeDaggerScope::class)
 * class MyActivity {
 *
 * }
 * ```
 */
annotation class InjectWith(
    /** The parent component scope to the contributed subcomponent */
    val scope: KClass<*>,

    /** (optional) The binding class key to bind the dagger component */
    val bindingKey: KClass<*> = Unit::class,
)
