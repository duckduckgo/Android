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

package com.duckduckgo.securestorage.api

/**
 * Public data class that wraps all data related to a website login.
 *
 * [details] contains all l1 encrypted attributes
 * [password] plain text password. All succeeding l2 and above attributes should be added here directly.
 */
data class WebsiteLoginCredentials(
    val details: WebsiteLoginDetails,
    val password: String?
)

/**
 * Public data class that wraps all data that should only be covered with l1 encryption.
 * All attributes is in plain text. Also, all should not require user authentication to be decrypted.
 *
 * [domain] url/name associated to a website login.
 * [username] used to populate the username fields in a login
 * [id] database id associated to the website login
 */
data class WebsiteLoginDetails(
    val domain: String?,
    val username: String?,
    val id: Int? = null
)
