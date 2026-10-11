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

package com.duckduckgo.codegen.annotations

import kotlin.reflect.KClass

/**
 * KSP annotation to generate the injector for an Android type like an Activity, Fragment, Service, etc.
 * For most scopes this is a graph extension contributed to the parent graph; for `ActivityScope` it is an `X_Injector` that adds an
 * `inject` function to the shared `ActivityComponent`.
 *
 * Usage:
 * ```kotlin
 * @InjectWith(SomeScope::class)
 * class MyActivity {
 *
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class InjectWith(
    /**
     * The scope of the generated graph extension (e.g. `FragmentScope`). Its parent graph follows from it: Fragment and View scopes
     * contribute to `ActivityScope`, other scopes to `AppScope`. `ActivityScope` adds an `inject` function to the shared
     * `ActivityComponent` instead.
     */
    val scope: KClass<*>,

    /**
     * (optional) The class key the injector factory is registered under in the parent's `injectorFactoryMap`.
     * Defaults to the annotated class.
     */
    val bindingKey: KClass<*> = Unit::class,
)
