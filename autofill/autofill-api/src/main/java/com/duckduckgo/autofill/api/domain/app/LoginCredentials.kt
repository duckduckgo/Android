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

package com.duckduckgo.autofill.api.domain.app

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.Serializable

/**
 * Representation of login credentials used for autofilling into the browser.
 */
@Parcelize
data class LoginCredentials(
    val id: Long? = null,
    val domain: String?,
    val username: String?,
    val password: String?,
    val domainTitle: String? = null,
    val notes: String? = null,
    val lastUpdatedMillis: Long? = null,
    val lastUsedMillis: Long? = null,
) : Parcelable, Serializable {
    override fun toString(): String {
        return """
            LoginCredentials(
                id=$id, domain=$domain, username=$username, password=********, domainTitle=$domainTitle,
                lastUpdatedMillis=$lastUpdatedMillis, lastUsedMillis=$lastUsedMillis, notesLength=${notes?.length ?: 0}
            )
        """.trimIndent()
    }
}
