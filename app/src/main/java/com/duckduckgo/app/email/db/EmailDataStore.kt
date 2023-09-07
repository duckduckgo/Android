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

package com.duckduckgo.app.email.db

/**
 * Data store for Email Protection (duck addresses)
 *
 * Provides ability to store and retrieve data related to the duck address feature such as personal username, next alias etc...
 */
interface EmailDataStore {
    var emailToken: String?
    var nextAlias: String?
    var emailUsername: String?
    var cohort: String?
    var lastUsedDate: String?
    fun canUseEncryption(): Boolean
}
