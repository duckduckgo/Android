/*
 * Copyright (c) 2023 DuckDuckGo
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

import com.duckduckgo.anvil.annotations.ContributesRemoteFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue.FALSE
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue.TRUE

/**
 * This is the class that represents the browser feature flags
 */
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "androidBrowserConfig",
)
interface AndroidBrowserConfigFeature {
    /**
     * @return `true` when the remote config has the global "androidBrowserConfig" feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    /**
     * @return `true` when the remote config has the global "collectFullWebViewVersion" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun collectFullWebViewVersion(): Toggle

    /**
     * @return `true` when the remote config has the global "screenLock" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun screenLock(): Toggle

    /**
     * @return `true` when the remote config has the global "optimizeTrackerEvaluationV2" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun optimizeTrackerEvaluationV2(): Toggle

    /**
     * @return `true` when the remote config has the global "errorPagePixel" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `true`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun errorPagePixel(): Toggle

    /**
     * @return `true` when the remote config has the global "featuresRequestHeader" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun featuresRequestHeader(): Toggle

    /**
     * @return `true` when the remote config has the global "webLocalStorage" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun webLocalStorage(): Toggle

    /**
     * @return `true` when the remote config has the global "indexedDB" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun indexedDB(): Toggle

    /**
     * @return `true` when the remote config has the global "enableMaliciousSiteProtection" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun enableMaliciousSiteProtection(): Toggle

    /**
     * @return `true` when the remote config has the global "fireproofedWebLocalStorage" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun fireproofedWebLocalStorage(): Toggle

    /**
     * @return `true` when the remote config has the global "fireproofedIndexedDB" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun fireproofedIndexedDB(): Toggle

    /**
     * @return `true` when the remote config has the global "httpError5xxPixel" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun httpError5xxPixel(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun glideSuspend(): Toggle

    /**
     * @return `true` when the remote config has the global "omnibarAnimation" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun omnibarAnimation(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun storeFaviconSuspend(): Toggle

    /**
     * @return `true` when the remote config has the global "checkMaliciousAfterHttpsUpgrade" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `true`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun checkMaliciousAfterHttpsUpgrade(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun newThreatProtectionSettings(): Toggle

    /**
     * Kill switch for INTENT_SCHEME handling in SpecialUrlDetector
     * @return `true` when the remote config has the global "handleIntentScheme" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `true`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun handleIntentScheme(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun hideDuckAiInSerpKillSwitch(): Toggle

    /**
     * Kill switch for intent resolution validation in SpecialUrlDetector
     * @return `true` when the remote config has the global "validateIntentResolution" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `true`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun validateIntentResolution(): Toggle

    /**
     * Kill switch to prevent changing the onboarding stage once AppStage.ESTABLISHED is reached
     * @return `true` when the remote config has the global "establishedAppStageGuard" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `true`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.TRUE)
    fun establishedAppStageGuard(): Toggle

    /**
     * @return `true` when the remote config has the global "vpnMenuItem" androidBrowserConfig
     * sub-feature flag enabled
     * If the remote feature is not present defaults to `false`
     */
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun vpnMenuItem(): Toggle

    @Toggle.DefaultValue(FALSE)
    fun onlyUpdateScriptOnProtectionsChanged(): Toggle
}
