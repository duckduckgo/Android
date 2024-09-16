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

package com.duckduckgo.mobile.android.vpn

class FakeVpnFeaturesRegistry : VpnFeaturesRegistry {
    private val features = mutableSetOf<String>()

    override suspend fun registerFeature(feature: VpnFeature) {
        features.add(feature.featureName)
    }

    override suspend fun unregisterFeature(feature: VpnFeature) {
        features.remove(feature.featureName)
    }

    override suspend fun isFeatureRunning(feature: VpnFeature): Boolean {
        return features.contains(feature.featureName)
    }

    override suspend fun isFeatureRegistered(feature: VpnFeature): Boolean {
        return isFeatureRunning(feature)
    }

    override suspend fun isAnyFeatureRunning(): Boolean {
        return features.isNotEmpty()
    }

    override suspend fun isAnyFeatureRegistered(): Boolean {
        return isAnyFeatureRunning()
    }

    override suspend fun refreshFeature(feature: VpnFeature) {
        // no-op
    }

    override suspend fun getRegisteredFeatures(): List<VpnFeature> {
        return features.map { VpnFeature { it } }
    }
}
