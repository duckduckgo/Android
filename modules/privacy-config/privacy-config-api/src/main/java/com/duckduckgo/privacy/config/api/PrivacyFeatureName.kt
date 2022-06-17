/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.api

import com.duckduckgo.feature.toggles.api.FeatureName

/** List of [FeatureName] that belong to the Privacy Configuration */
enum class PrivacyFeatureName(override val value: String) : FeatureName {
    ContentBlockingFeatureName("contentBlocking"),
    GpcFeatureName("gpc"),
    HttpsFeatureName("https"),
    TrackerAllowlistFeatureName("trackerAllowlist"),
    DrmFeatureName("eme"),
    AmpLinksFeatureName("ampLinks"),
    TrackingParametersFeatureName("trackingParameters"),
    AutofillFeatureName("autofill"),
    UserAgentFeatureName("customUserAgent"),
}
