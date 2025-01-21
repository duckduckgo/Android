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

/** List of [PrivacyFeatureName] that belong to the Privacy Configuration */
enum class PrivacyFeatureName(val value: String) {
    ContentBlockingFeatureName("contentBlocking"),
    GpcFeatureName("gpc"),
    HttpsFeatureName("https"),
    TrackerAllowlistFeatureName("trackerAllowlist"),
    DrmFeatureName("eme"),
    AmpLinksFeatureName("ampLinks"),
    TrackingParametersFeatureName("trackingParameters"),
}

const val PRIVACY_REMOTE_CONFIG_URL = "https://staticcdn.duckduckgo.com/trackerblocking/config/v4/android-config.json"

// control
// const val PRIVACY_REMOTE_CONFIG_URL = "https://www.jsonblob.com/api/1329816455226777600"

// variant 1
// const val PRIVACY_REMOTE_CONFIG_URL = "https://www.jsonblob.com/api/1329805206535462912"

// variant 2
// const val PRIVACY_REMOTE_CONFIG_URL = "https://www.jsonblob.com/api/1329040942036082688"

// variant 3
// const val PRIVACY_REMOTE_CONFIG_URL = "https://www.jsonblob.com/api/1329809981419216896"
