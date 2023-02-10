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
 * Anvil annotation to generate remote features
 *
 * Usage:
 * ```kotlin
 * @ContributesRemoteFeature(
 *   scope = AppScope::class,
 *   featureName = "myFeatureName",
 *   settingsStore = MyFeatureSettingsStore::class,
 *   exceptionStore = MyFeatureExceptionStore::class,
 * )
 * interface MyFeature {
 *
 * }
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ContributesRemoteFeature(
    /** The scope in which to include this contributed PluginPoint */
    val scope: KClass<*>,

    /**
     * Type that the feature will be bound to
     */
    val boundType: KClass<*> = Unit::class,

    /** The name of the remote feature */
    val featureName: String,

    /** The class that implements the [RemoteFeatureSettingsStore] interface */
    val settingsStore: KClass<*> = Unit::class,

    /** The class that implements the [RemoteFeatureExceptionStore] interface */
    val exceptionsStore: KClass<*> = Unit::class,
)
