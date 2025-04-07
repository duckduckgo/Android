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

package com.duckduckgo.autofill.impl.ui.credential.selecting

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import javax.inject.Named

interface TimestampBasedLoginSorter : Comparator<LoginCredentials>

@ContributesBinding(AppScope::class)
@Named("LastUsedCredentialSorter")
class LastUsedCredentialSorter @Inject constructor() : TimestampBasedLoginSorter {

    /**
     * This comparator sorts based on last-used timestamps.
     *
     * Null timestamps come first, then older timestamps are sorted before newer timestamps.
     */
    override fun compare(
        o1: LoginCredentials?,
        o2: LoginCredentials?,
    ): Int {
        // handle where one or both of the objects are null
        if (o1 == null && o2 == null) return 0
        if (o1 == null) return -1
        if (o2 == null) return 1

        // handle where one or both of the timestamps are null
        val lastUsed1 = o1.lastUsedMillis
        val lastUsed2 = o2.lastUsedMillis

        if (lastUsed1 == null && lastUsed2 == null) return 0
        if (lastUsed1 == null) return -1
        if (lastUsed2 == null) return 1

        return lastUsed1.compareTo(lastUsed2)
    }
}

@ContributesBinding(AppScope::class)
@Named("LastUpdatedCredentialSorter")
class LastUpdatedCredentialSorter @Inject constructor() : TimestampBasedLoginSorter {

    /**
     * This comparator sorts based on last-updated timestamps.
     *
     * Null timestamps come first, then older timestamps are sorted before newer timestamps.
     */
    override fun compare(
        o1: LoginCredentials?,
        o2: LoginCredentials?,
    ): Int {
        // handle where one or both of the objects are null
        if (o1 == null && o2 == null) return 0
        if (o1 == null) return -1
        if (o2 == null) return 1

        // handle where one or both of the timestamps are null
        val lastUpdated1 = o1.lastUpdatedMillis
        val lastUpdated2 = o2.lastUpdatedMillis

        if (lastUpdated1 == null && lastUpdated2 == null) return 0
        if (lastUpdated1 == null) return -1
        if (lastUpdated2 == null) return 1

        return lastUpdated1.compareTo(lastUpdated2)
    }
}
