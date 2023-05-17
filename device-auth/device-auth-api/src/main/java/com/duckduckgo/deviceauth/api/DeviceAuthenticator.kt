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

package com.duckduckgo.deviceauth.api

import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

interface DeviceAuthenticator {
    /**
     * This method can be used to check if the user's device has a valid device authentication enrolled (Fingerprint, PIN, pattern or password).
     */
    fun hasValidDeviceAuthentication(): Boolean

    /**
     * Launches a device authentication flow for a specific [featureToAuth] from a [fragment]. [onResult] can be used to
     * communicate back to the feature the result of the flow.
     */
    @UiThread
    fun authenticate(
        featureToAuth: Features,
        fragment: Fragment,
        onResult: (AuthResult) -> Unit,
    )

    /**
     * Launches a device authentication flow for a specific [featureToAuth] from a [fragmentActivity]. [onResult] can be used to
     * communicate back to the feature the result of the flow.
     */
    @UiThread
    fun authenticate(
        featureToAuth: Features,
        fragmentActivity: FragmentActivity,
        onResult: (AuthResult) -> Unit,
    )

    sealed class AuthResult {
        object Success : AuthResult()
        object UserCancelled : AuthResult()
        data class Error(val reason: String) : AuthResult()
    }

    enum class Features {
        AUTOFILL_TO_USE_CREDENTIALS,
        AUTOFILL_TO_ACCESS_CREDENTIALS,
    }
}
