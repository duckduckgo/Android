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

import kotlinx.coroutines.flow.Flow

/**
 * Use this class to register features that required VPN access.
 *
 * Registering a feature will cause the VPN to be enabled.
 * Unregistering a feature will cause the VPN to be disabled IF no other feature is registered.
 */
interface VpnFeaturesRegistry {

    /**
     * Call this method to register a feature that requires VPN access.
     * If the VPN is not enabled, it will be enabled.
     */
    fun registerFeature(feature: VpnFeature)

    /**
     * Call this method to unregister a feature that requires VPN access.
     * If the VPN will be disabled if and only if this is the last registered feature.
     */
    fun unregisterFeature(feature: VpnFeature)

    /**
     * @return `true` if this feature is registered, `false` otherwise.
     */
    fun isFeatureRegistered(feature: VpnFeature): Boolean

    /**
     * @return returns `true` if there's any feature currently using the VPN, `false` otherwise
     */
    fun isAnyFeatureRegistered(): Boolean

    /**
     * Refreshing the feature will cause the VPN to be stopped/restarted if it is enabled and the feature is already registered.
     */
    suspend fun refreshFeature(feature: VpnFeature)

    /**
     * Subscribe to this flow to get changes of the VPN feature registrations.
     * The flow is a hot flow with no cache, ie. only emits new values upon subscription.
     *
     * When a VPN feature is (re)registered it will emit a pair with the name of the feature and `true`
     * When a VPN feature is (re)unregistered it will emit a pair with the name of the feature and `false`
     */
    fun registryChanges(): Flow<Pair<String, Boolean>>

    /**
     * Get the last known registry change at the time this function is called.
     *
     *  @return Pair where first is the VPNFeature name and second is the boolean representing if feature is enabled or not.
     */
    fun getLastRegistryChange(): Pair<String, Boolean>

    fun getRegisteredFeatures(): List<VpnFeature>
}

interface VpnFeature {
    val featureName: String
}

/**
 * Fake constructor for [VpnFeature] from the passed in [block] lambda
 * instead of using the anonymous `object : VpnFeature` syntax.
 *
 * Usage:
 *
 * ```kotlin
 * val name = VpnFeature {
 *
 * }
 * ```
 */
fun VpnFeature(block: () -> String): VpnFeature = object : VpnFeature {
    override val featureName: String = block()
}
