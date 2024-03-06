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

package com.duckduckgo.experiments.api

/** Public interface for variant manager feature*/
interface VariantManager {
    /**
     * Returns the default variant key
     */
    fun defaultVariantKey(): String

    /**
     * Returns the variant key assigned to the user
     */
    fun getVariantKey(): String?

    /**
     * Updates user experimental variant when referralResultReceived from PlayStore
     */
    fun updateAppReferrerVariant(variant: String)

    /**
     * Updated experimental variants received
     *
     * @param variants Updated list of VariantConfig objects
     */
    fun updateVariants(variants: List<VariantConfig>)
}
