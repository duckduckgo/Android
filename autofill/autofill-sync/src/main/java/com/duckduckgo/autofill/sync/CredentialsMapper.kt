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

import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.sync.api.SyncCrypto
import javax.inject.Inject

class SyncCredentialsMapper @Inject constructor(
    private val syncCrypto: SyncCrypto,
) {
    fun toLoginCredential(
        entry: LoginCredentialEntryResponse,
        localId: Long? = null,
    ): LoginCredentials {
        return LoginCredentials(
            id = localId,
            domain = entry.domain?.decrypt(),
            username = entry.username?.decrypt(),
            password = entry.password?.decrypt(),
            domainTitle = entry.title.decrypt(),
            notes = entry.notes.decrypt(),
            lastUpdatedMillis = DatabaseDateFormatter.parseIso8601ToMillis(entry.last_modified!!),
        )
    }

    private fun String?.decrypt(): String {
        return if (this == null) "" else syncCrypto.decrypt(this)
    }
}
