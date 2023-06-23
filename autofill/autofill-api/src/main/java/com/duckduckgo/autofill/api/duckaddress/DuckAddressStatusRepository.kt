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

package com.duckduckgo.autofill.api.duckaddress

/**
 * Repository for Duck Address activation status
 *
 * Provides ability to get and set activation status for a private duck address
 */
interface DuckAddressStatusRepository {

    /**
     * Get activation status for a private duck address
     *
     * @return [ActivationStatusResult] representing the activation status for the private duck address
     */
    suspend fun getActivationStatus(privateDuckAddress: String): ActivationStatusResult

    /**
     * Set activation status for a private duck address
     *
     * @return true if the activation status was successfully set, false otherwise
     */
    suspend fun setActivationStatus(privateDuckAddress: String, isActivated: Boolean): Boolean

    sealed interface ActivationStatusResult {
        object NotSignedIn : ActivationStatusResult
        object Activated : ActivationStatusResult
        object Deactivated : ActivationStatusResult
        object Unmanageable : ActivationStatusResult
        object GeneralError : ActivationStatusResult
    }
}
