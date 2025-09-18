/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.downloads.impl.feature

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

/**
 * This is the class that represents file download feature flags and kill switches
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "androidFileDownloadFeature",
)
interface FileDownloadFeature {

    // self() is required, but it is not currently used in the codebase
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle

    // This kill switch can be used to revert to the old behaviour of sending an empty Cookie header instead of omitting it
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun omitEmptyCookieHeader(): Toggle
}
