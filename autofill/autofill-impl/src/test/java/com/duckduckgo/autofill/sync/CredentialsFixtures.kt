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

package com.duckduckgo.autofill.sync

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.securestorage.WebsiteLoginDetails
import com.duckduckgo.autofill.impl.securestorage.WebsiteLoginDetailsWithCredentials

object CredentialsFixtures {
    val twitterCredentials = LoginCredentials(
        id = 1L,
        domain = "www.twitter.com",
        username = "twitterUS",
        password = "twitterPW",
        domainTitle = "Twitter",
        notes = "My Twitter account",
    )

    val spotifyCredentials = LoginCredentials(
        id = 2L,
        domain = "www.spotify.com",
        username = "spotifyUS",
        password = "spotifyPW",
        domainTitle = "Spotify",
        notes = "My Spotify account",
    )

    val amazonCredentials = LoginCredentials(
        id = 3L,
        domain = "www.amazon.com",
        username = "amazonUS",
        password = "amazonPW",
        domainTitle = "Amazon",
        notes = "My Amazon account",
    )

    val invalidCredentials = LoginCredentials(
        id = 4L,
        domain = "www.invalid.com",
        username = "invalidUS",
        password = "invalidPW",
        domainTitle = String(CharArray(3000) { 'a' + (it % 26) }),
        notes = "My Invalid account",
    )

    fun LoginCredentials.toLoginCredentialEntryResponse(): CredentialsSyncEntryResponse =
        CredentialsSyncEntryResponse(
            id = id.toString(),
            domain = domain,
            username = username,
            password = password,
            title = domainTitle,
            notes = notes,
            last_modified = null,
        )

    fun WebsiteLoginDetailsWithCredentials.toLoginCredentials(): LoginCredentials {
        return LoginCredentials(
            id = details.id,
            domain = details.domain,
            username = details.username,
            password = password,
            domainTitle = details.domainTitle,
            notes = notes,
            lastUpdatedMillis = details.lastUpdatedMillis,
            lastUsedMillis = details.lastUsedInMillis,
        )
    }
    fun LoginCredentials.toWebsiteLoginCredentials(): WebsiteLoginDetailsWithCredentials {
        return WebsiteLoginDetailsWithCredentials(
            details = WebsiteLoginDetails(
                domain = domain,
                username = username,
                id = id,
                domainTitle = domainTitle,
                lastUpdatedMillis = lastUpdatedMillis,
                lastUsedInMillis = lastUsedMillis,
            ),
            password = password,
            notes = notes,
        )
    }
}
