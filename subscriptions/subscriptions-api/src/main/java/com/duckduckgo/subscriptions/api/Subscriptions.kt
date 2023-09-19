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

package com.duckduckgo.subscriptions.api

interface Subscriptions {

    /**
     * This method returns a sealed class with the PAT for the authenticated user or an error if it doesn't exist
     * or any errors arise.
     * @return [PatResult]
     */
    suspend fun getPAT(): PatResult

    /**
     * This method returns a [true] if a  given [product] can be found in the entitlements list or [false] otherwise
     * @return [Boolean]
     */
    suspend fun hasEntitlement(product: String): Boolean
}

/**
 * Sealed class to be return when a PAT is requests. It could return either a [Success] with the PAT or [Failure] with
 * an error message
 */
sealed class PatResult {
    data class Success(val pat: String) : PatResult()
    data class Failure(val message: String) : PatResult()
}
