/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.vpn.internal.feature.rules

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerExceptionRule
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ExclusionRulesRepository @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    vpnDatabase: VpnDatabase
) {
    private val blockingDao = vpnDatabase.vpnAppTrackerBlockingDao()

    suspend fun upsertRule(
        appPackageName: String,
        domain: String
    ) = withContext(dispatcherProvider.io()) {
        val rule = blockingDao.getRuleByTrackerDomain(domain)
        if (rule != null) {
            val updatedRule = rule.copy(
                rule = rule.rule,
                packageNames = rule.packageNames.toMutableSet().apply { add(appPackageName) }.toList()
            )
            blockingDao.insertTrackerExceptionRules(listOf(updatedRule))
        } else {
            blockingDao.insertTrackerExceptionRules(
                listOf(AppTrackerExceptionRule(rule = domain, packageNames = listOf(appPackageName)))
            )
        }
    }

    suspend fun removeRule(
        appPackageName: String,
        domain: String
    ) = withContext(dispatcherProvider.io()) {
        val rule = blockingDao.getRuleByTrackerDomain(domain)
        rule?.let {
            val updatedRule = it.copy(
                rule = it.rule,
                packageNames = it.packageNames.toMutableSet().apply { remove(appPackageName) }.toList()
            )
            blockingDao.insertTrackerExceptionRules(listOf(updatedRule))
        }
    }

    suspend fun getAllTrackerRules(): List<AppTrackerExceptionRule> = withContext(dispatcherProvider.io()) {
        return@withContext blockingDao.getTrackerExceptionRules()
    }

    suspend fun deleteAllTrackerRules() = withContext(dispatcherProvider.io()) {
        blockingDao.deleteTrackerExceptionRules()
    }

    suspend fun insertTrackerRules(rules: List<AppTrackerExceptionRule>) = withContext(dispatcherProvider.io()) {
        blockingDao.insertTrackerExceptionRules(rules)
    }
}
