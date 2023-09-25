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

package com.duckduckgo.feature.toggles.codegen

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultValue
import com.duckduckgo.feature.toggles.api.Toggle.InternalAlwaysEnabled

abstract class TriggerTestScope private constructor()

@ContributesRemoteFeature(
    scope = TriggerTestScope::class,
    featureName = "testFeature",
)
interface TestTriggerFeature {
    @DefaultValue(false)
    fun self(): Toggle

    @DefaultValue(false)
    fun fooFeature(): Toggle

    @DefaultValue(true)
    @InternalAlwaysEnabled
    fun internalDefaultTrue(): Toggle

    @DefaultValue(false)
    @InternalAlwaysEnabled
    fun internalDefaultFalse(): Toggle

    @DefaultValue(true)
    fun defaultTrue(): Toggle

    @DefaultValue(false)
    fun defaultFalse(): Toggle

    @DefaultValue(false)
    fun variantFeature(): Toggle
}
