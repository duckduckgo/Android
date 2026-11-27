/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.auditlog

import com.duckduckgo.sync.impl.AuditLogPage

sealed interface AuditLogResult {
    data class Success(val page: AuditLogPage) : AuditLogResult

    /** 418 or 429. The server sends 418 with an empty body when the per-account quota is used up. Keep the cursor and retry later. */
    data class RateLimited(val statusCode: Int) : AuditLogResult

    /** Any 400. The cursor can no longer be used: drop it and start again with no cursor. */
    data object InvalidCursor : AuditLogResult

    /** 401 or 403. A 401 for the current token has already signed the device out, as for every other sync call. */
    data class AuthError(val statusCode: Int) : AuditLogResult

    /** Any other failure from the server, or a response we could not read. [statusCode] is null when there was no usable response. */
    data class ServerError(val statusCode: Int?) : AuditLogResult

    data object NetworkError : AuditLogResult
}
