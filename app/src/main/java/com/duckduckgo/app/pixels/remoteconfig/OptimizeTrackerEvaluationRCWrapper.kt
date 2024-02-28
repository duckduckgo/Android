/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.pixels.remoteconfig

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface OptimizeTrackerEvaluationRCWrapper {
    val enabled: Boolean
}

@ContributesBinding(AppScope::class)
class RealOptimizeTrackerEvaluationRCWrapper @Inject constructor(
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
) : OptimizeTrackerEvaluationRCWrapper {
    override val enabled by lazy { androidBrowserConfigFeature.optimizeTrackerEvaluationV2().isEnabled() }
}
