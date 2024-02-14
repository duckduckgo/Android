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

package com.duckduckgo.autofill.impl.securestorage

/**
 * Public data class that wraps all data related to a website login. All succeeding l2 and above attributes should be added here directly.
 *
 * [details] contains all l1 encrypted attributes
 * [password] plain text password.
 * [notes] plain text notes associated to a login credential
 */
data class WebsiteLoginDetailsWithCredentials(
    val details: WebsiteLoginDetails,
    val password: String?,
    val notes: String? = null,
) {
    override fun toString(): String {
        return """
            WebsiteLoginDetailsWithCredentials(
                "details=$details, password=********, notesLength=${notes?.length ?: 0}"
            )
        """.trimIndent()
    }
}

/**
 * Public data class that wraps all data that should only be covered with l1 encryption.
 * All attributes is in plain text. Also, all should not require user authentication to be decrypted.
 *
 * [domain] url/name associated to a website login.
 * [username] used to populate the username fields in a login
 * [id] database id associated to the website login
 * [domainTitle] title associated to the login
 * [lastUpdatedMillis] time in milliseconds when the credential was last updated
 * [lastUsedInMillis] time in milliseconds when the credential was last used to autofill
 */
data class WebsiteLoginDetails(
    val domain: String?,
    val username: String?,
    val id: Long? = null,
    val domainTitle: String? = null,
    val lastUpdatedMillis: Long? = null,
    val lastUsedInMillis: Long? = null,
) {
    override fun toString(): String {
        return """
            WebsiteLoginDetails(
                id=$id, username=$username, domainTitle=$domainTitle, lastUpdatedMillis=$lastUpdatedMillis
            )
        """.trimIndent()
    }
}
