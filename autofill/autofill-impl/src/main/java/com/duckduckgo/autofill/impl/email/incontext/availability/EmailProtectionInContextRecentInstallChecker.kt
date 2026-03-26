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

package com.duckduckgo.autofill.impl.email.incontext.availability

import com.duckduckgo.autofill.impl.email.incontext.store.EmailProtectionInContextDataStore
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface EmailProtectionInContextRecentInstallChecker {
    suspend fun isRecentInstall(): Boolean
}

@ContributesBinding(AppScope::class)
class RealEmailProtectionInContextRecentInstallChecker @Inject constructor(
    private val userBrowserProperties: UserBrowserProperties,
    private val dataStore: EmailProtectionInContextDataStore,
    private val dispatchers: DispatcherProvider,
) : EmailProtectionInContextRecentInstallChecker {

    override suspend fun isRecentInstall(): Boolean = withContext(dispatchers.io()) {
        val maxInstalledDays = dataStore.getMaximumPermittedDaysSinceInstallation()
        val daysSinceInstall = userBrowserProperties.daysSinceInstalled()

        return@withContext daysSinceInstall <= maxInstalledDays
    }
}
