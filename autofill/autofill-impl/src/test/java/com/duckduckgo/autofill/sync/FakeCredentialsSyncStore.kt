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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class FakeCredentialsSyncStore : CredentialsSyncStore {
    override var syncPausedReason: String = ""
    override var serverModifiedSince: String = "0"
    override var startTimeStamp: String = "0"
    override var clientModifiedSince: String = "0"
    override var isSyncPaused: Boolean = false
    override fun isSyncPausedFlow(): Flow<Boolean> = emptyFlow()
    override var invalidEntitiesIds: List<String> = emptyList()
}
