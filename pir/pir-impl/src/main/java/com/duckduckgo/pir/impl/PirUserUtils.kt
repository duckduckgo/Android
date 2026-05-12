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

package com.duckduckgo.pir.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.checker.PirWorkHandler
import com.duckduckgo.pir.impl.store.PirRepository
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

interface PirUserUtils {
    suspend fun isActiveUser(): Boolean
}

@ContributesBinding(AppScope::class)
class RealPirUserUtils @Inject constructor(
    private val pirWorkHandler: PirWorkHandler,
    private val pirRepository: PirRepository,
) : PirUserUtils {

    /**
     * A user is considered active if they:
     * - Have a valid subscription
     * - Has PIR enabled
     * - Has a userprofile set up for scanning
     */
    override suspend fun isActiveUser(): Boolean {
        return pirWorkHandler.canRunPir().firstOrNull() == true && pirRepository.getValidUserProfileQueries().isNotEmpty()
    }
}
