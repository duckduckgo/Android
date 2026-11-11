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

package com.duckduckgo.devicemigration.impl

import com.duckduckgo.codegen.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "deviceMigration",
)
interface DeviceMigrationFeature {
    /**
     * Kill switch for the migration experience on a new phone. Enabled by default because the OS transfers the data whether or
     * not this is on, and without the experience the transferred state is left as the previous phone's.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle
}
