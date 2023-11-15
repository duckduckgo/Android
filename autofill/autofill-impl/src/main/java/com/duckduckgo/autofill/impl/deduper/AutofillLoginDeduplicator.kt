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
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AutofillLoginDeduplicator {

    fun deduplicate(
        originalUrl: String,
        logins: List<LoginCredentials>,
    ): List<LoginCredentials>
}

@ContributesBinding(AppScope::class)
class RealAutofillLoginDeduplicator @Inject constructor(
    private val usernamePasswordMatcher: AutofillDeduplicationUsernameAndPasswordMatcher,
    private val bestMatchFinder: AutofillDeduplicationBestMatchFinder,
) : AutofillLoginDeduplicator {

    override fun deduplicate(
        originalUrl: String,
        logins: List<LoginCredentials>,
    ): List<LoginCredentials> {
        val dedupedLogins = mutableListOf<LoginCredentials>()

        val groups = usernamePasswordMatcher.groupDuplicateCredentials(logins)
        groups.forEach {
            val bestMatchForGroup = bestMatchFinder.findBestMatch(originalUrl, it.value)
            if (bestMatchForGroup != null) {
                dedupedLogins.add(bestMatchForGroup)
            }
        }

        return dedupedLogins
    }
}
