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

package com.duckduckgo.adblocking.impl

import com.duckduckgo.adblocking.impl.store.AdBlockingUserPreferences
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface AdBlockingSettingsRepository {

    fun isEnabledFlow(): Flow<Boolean?>
    suspend fun setEnabled(enabled: Boolean)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAdBlockingSettingsRepository @Inject constructor(
    private val userPreferences: AdBlockingUserPreferences,
) : AdBlockingSettingsRepository {

    override fun isEnabledFlow(): Flow<Boolean?> = userPreferences.isEnabledFlow()

    override suspend fun setEnabled(enabled: Boolean) {
        userPreferences.setEnabled(enabled)
    }
}
