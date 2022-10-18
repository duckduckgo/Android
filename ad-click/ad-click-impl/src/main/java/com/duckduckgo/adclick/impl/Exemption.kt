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

package com.duckduckgo.adclick.impl

data class Exemption(
    val hostTldPlusOne: String,
    val navigationExemptionDeadline: Long,
    val exemptionDeadline: Long,
    val adClickActivePixelFired: Boolean = false,
) {
    fun isExpired(): Boolean {
        val now = System.currentTimeMillis()
        if (exemptionDeadline < now) {
            return true
        }
        return navigationExemptionDeadline != NO_EXPIRY && navigationExemptionDeadline < now
    }

    companion object {
        const val NO_EXPIRY = -1L
    }
}
