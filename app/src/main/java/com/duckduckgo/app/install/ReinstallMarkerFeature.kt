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

package com.duckduckgo.app.install

import com.duckduckgo.codegen.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

/**
 * Controls which launches count as visible before the reinstall marker is written. A new install's first launches run before the
 * remote config arrives, so the defaults are what applies to them.
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "reinstallMarkerAfterVisibleLaunch",
)
interface ReinstallMarkerFeature {
    /**
     * Off: the marker is written without waiting for a visible launch.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun self(): Toggle

    /**
     * A launch on the built-in screen or a public secondary display (Samsung DeX, an external screen) always counts, and one on a
     * private display never does. On: a launch whose display can't be read doesn't count either. Off: it counts.
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun strictMode(): Toggle
}
