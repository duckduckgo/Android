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

package com.duckduckgo.app.branddesignupdate

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

/**
 * App-wide brand design update (rebrand) feature flag.
 *
 * Gate rebrand changes on [feature] via `feature().isEnabled()`, never on [self]:
 * self() cannot use the incremental rollout mechanism.
 *
 * Interim scaffolding for the brand design update. The follow-up theme project promotes
 * the new styles into the base themes and deletes this flag and its supporting code.
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "appBrandDesignUpdate",
)
interface AppBrandDesignUpdateToggles {

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun feature(): Toggle
}
