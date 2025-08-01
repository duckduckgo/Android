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

package com.duckduckgo.pir.internal.scripts.models

import com.duckduckgo.pir.internal.models.ProfileQuery

data class PirScriptRequestParams(
    val state: ActionRequest,
)

data class ActionRequest(
    val action: BrokerAction,
    val data: PirScriptRequestData? = null,
)

sealed class PirScriptRequestData {
    data class SolveCaptcha(
        val token: String,
    ) : PirScriptRequestData()

    data class UserProfile(
        val userProfile: ProfileQuery? = null,
        val extractedProfile: ExtractedProfileParams? = null,
    ) : PirScriptRequestData()
}

data class ExtractedProfileParams(
    val id: Int? = null,
    val name: String? = null,
    val profileUrl: String? = null,
    val email: String? = null,
    val fullName: String? = null,
)
