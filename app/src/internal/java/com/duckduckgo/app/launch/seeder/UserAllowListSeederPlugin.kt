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

package com.duckduckgo.app.launch.seeder

import android.util.Log
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.testseeder.api.TestSeederKey
import com.duckduckgo.testseeder.api.TestSeederPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

// Ships in every internal build, but RealTestScenarioSeeder only invokes plugins when the launch
// intent carries isMaestro/isMacrobenchmark=true, a normal launch never sets that, so apply() is inert.
@ContributesMultibinding(AppScope::class)
class UserAllowListSeederPlugin @Inject constructor(
    private val userAllowListRepository: UserAllowListRepository,
) : TestSeederPlugin {

    override val handledKeys = setOf(TestSeederKey.USER_ALLOW_LIST.key)

    override suspend fun apply(key: String, value: String) {
        userAllowListRepository.domainsInUserAllowList().forEach {
            userAllowListRepository.removeDomainFromUserAllowList(it)
        }

        val requested = value.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        requested.forEach { userAllowListRepository.addDomainToUserAllowList(it) }

        val requestedSet = requested.toSet()
        val observed = userAllowListRepository.domainsInUserAllowListFlow().firstOrNull { it.toSet() == requestedSet }
            ?: error("userAllowList never settled on \"$requestedSet\"")
        Log.i("DdgTestSeeder", "userAllowList=" + observed.sorted().joinToString(","))
    }
}
