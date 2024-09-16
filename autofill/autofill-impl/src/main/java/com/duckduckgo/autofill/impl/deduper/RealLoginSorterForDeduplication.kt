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

package com.duckduckgo.autofill.impl.deduper

import com.duckduckgo.autofill.api.domain.app.LoginCredentials

class AutofillDeduplicationLoginComparator : Comparator<LoginCredentials> {
    override fun compare(
        o1: LoginCredentials,
        o2: LoginCredentials,
    ): Int {
        val lastModifiedComparison = compareLastModified(o1.lastUpdatedMillis, o2.lastUpdatedMillis)
        if (lastModifiedComparison != 0) return lastModifiedComparison

        // last updated matches, fallback to domain
        return compareDomains(o1.domain, o2.domain)
    }

    private fun compareLastModified(
        lastModified1: Long?,
        lastModified2: Long?,
    ): Int {
        if (lastModified1 == null && lastModified2 == null) return 0

        if (lastModified1 == null) return -1
        if (lastModified2 == null) return 1
        return lastModified2.compareTo(lastModified1)
    }

    private fun compareDomains(
        domain1: String?,
        domain2: String?,
    ): Int {
        if (domain1 == null && domain2 == null) return 0
        if (domain1 == null) return -1
        if (domain2 == null) return 1
        return domain1.compareTo(domain2)
    }
}
