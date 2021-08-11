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

interface PrivacyConfigDownloader {
    suspend fun download(): Boolean
}

sealed class PrivacyFeatureName(override val value: String) : FeatureName {
    data class ContentBlockingFeatureName(override val value: String = "contentBlocking") : PrivacyFeatureName(value)
    data class OtherFeatureName(override val value: String = "other") : PrivacyFeatureName(value)
}

data class TestFeatureName(override val value: String = "test") : FeatureName
