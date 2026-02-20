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

package com.duckduckgo.contentscopescripts.impl.features.eventhub

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle

/**
 * Remote feature toggle for the eventHub feature.
 *
 * The eventHub feature manages client-side telemetry that processes webEvent
 * notifications from C-S-S's webDetection feature. It maintains counters,
 * timers, and fires pixels based on remote configuration.
 *
 * This feature MUST NOT be disabled due to privacy protections being disabled.
 * The ONLY way to disable it is through an explicit disabled state (or feature absent)
 * in the remote configuration.
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "eventHub",
)
interface EventHubFeature {
    @Toggle.DefaultValue(Toggle.DefaultFeatureValue.FALSE)
    fun self(): Toggle
}
