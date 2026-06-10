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

package com.duckduckgo.app.browser.trafficquality

import com.duckduckgo.app.browser.trafficquality.remote.TrafficQualityAppVersion
import com.duckduckgo.app.browser.trafficquality.remote.TrafficQualityAppVersionFeatures

val currentVersion = 5210000
private val anotherVersion = 5220000

val configEnabledForAnotherVersion = TrafficQualityAppVersion(anotherVersion, 5, 5, featuresEnabled())
val configEnabledForCurrentVersion = TrafficQualityAppVersion(currentVersion, 5, 5, featuresEnabled())

fun noFeaturesEnabled(): TrafficQualityAppVersionFeatures {
    return TrafficQualityAppVersionFeatures(gpc = false, cpm = false, appTP = false, netP = false)
}

fun featuresEnabled(
    gpc: Boolean = false,
    cpm: Boolean = false,
    appTP: Boolean = false,
    netP: Boolean = false,
): TrafficQualityAppVersionFeatures {
    return TrafficQualityAppVersionFeatures(gpc = gpc, cpm = cpm, appTP = appTP, netP = netP)
}
