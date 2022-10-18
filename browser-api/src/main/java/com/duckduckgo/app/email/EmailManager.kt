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

package com.duckduckgo.app.email

import kotlinx.coroutines.flow.StateFlow

interface EmailManager {
    fun signedInFlow(): StateFlow<Boolean>
    fun getAlias(): String?
    fun isSignedIn(): Boolean
    fun storeCredentials(
        token: String,
        username: String,
        cohort: String,
    )

    fun signOut()
    fun getEmailAddress(): String?
    fun getUserData(): String

    fun getCohort(): String
    fun isEmailFeatureSupported(): Boolean
    fun getLastUsedDate(): String
    fun setNewLastUsedDate()
}
