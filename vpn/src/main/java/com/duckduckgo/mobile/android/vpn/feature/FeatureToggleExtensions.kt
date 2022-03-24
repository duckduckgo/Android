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

package com.duckduckgo.mobile.android.vpn.feature

import com.duckduckgo.feature.toggles.api.FeatureToggle

internal fun FeatureToggle.isNetworkSwitchingHandlingEnabled(): Boolean {
    return isFeatureEnabled(AppTpFeatureName.NetworkSwitchingHandling, false)
}
internal fun FeatureToggle.isIpv6SupportEnabled(): Boolean {
    return isFeatureEnabled(AppTpFeatureName.Ipv6Support, false)
}
internal fun FeatureToggle.isPrivateDnsSupportEnabled(): Boolean {
    return isFeatureEnabled(AppTpFeatureName.PrivateDnsSupport, false)
}
