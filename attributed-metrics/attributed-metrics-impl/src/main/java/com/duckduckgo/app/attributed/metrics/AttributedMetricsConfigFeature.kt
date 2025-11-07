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

package com.duckduckgo.app.attributed.metrics

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue

@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "attributedMetrics",
)
interface AttributedMetricsConfigFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun emitAllMetrics(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun retention(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun canEmitRetention(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun searchDaysAvg(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun canEmitSearchDaysAvg(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun searchCountAvg(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun canEmitSearchCountAvg(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun adClickCountAvg(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun canEmitAdClickCountAvg(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun aiUsageAvg(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun canEmitAIUsageAvg(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun subscriptionRetention(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun canEmitSubscriptionRetention(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun syncDevices(): Toggle
}
