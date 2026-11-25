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

package com.duckduckgo.app.browser.newaddressbaroption

import com.duckduckgo.codegen.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "duckAiToggleMigration",
)
interface DuckAiToggleMigrationFeature {

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle

    /** Kill switch: confirming Search only on the address bar picker records the input screen setting as off. */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun pickerSearchOnlyWritesSetting(): Toggle

    /** Turns Search & Duck.ai on, once, for existing Duck.ai users who were never asked. */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun migration(): Toggle
}
