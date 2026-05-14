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

package com.duckduckgo.privacy.config.store.features.trackerallowlist

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.privacy.config.store.AllowlistRuleEntity
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.TrackerAllowlistEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat

interface TrackerAllowlistRepository {
    fun updateAll(exceptions: List<TrackerAllowlistEntity>)
    val exceptions: List<TrackerAllowlistEntity>
    val rulesByDomain: Map<String, List<CompiledRule>>
}

data class CompiledRule(
    val rule: AllowlistRuleEntity,
    val regex: Regex?,
)

class RealTrackerAllowlistRepository(
    database: PrivacyConfigDatabase,
    coroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    isMainProcess: Boolean,
) : TrackerAllowlistRepository {

    private val trackerAllowlistDao: TrackerAllowlistDao = database.trackerAllowlistDao()

    @Volatile
    private var snapshot: Snapshot = Snapshot(emptyList(), emptyMap())

    override val exceptions get() = snapshot.exceptions
    override val rulesByDomain get() = snapshot.rulesByDomain

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (isMainProcess) {
                loadToMemory()
            }
        }
    }

    override fun updateAll(exceptions: List<TrackerAllowlistEntity>) {
        trackerAllowlistDao.updateAll(exceptions)
        loadToMemory()
    }

    private fun loadToMemory() {
        val fresh = trackerAllowlistDao.getAll()
        snapshot = Snapshot(fresh, buildRulesByDomain(fresh))
    }

    private data class Snapshot(
        val exceptions: List<TrackerAllowlistEntity>,
        val rulesByDomain: Map<String, List<CompiledRule>>,
    )
}

internal fun buildRulesByDomain(exceptions: List<TrackerAllowlistEntity>): Map<String, List<CompiledRule>> {
    val map = HashMap<String, List<CompiledRule>>(exceptions.size)
    exceptions.forEach { entity ->
        // Keys are www-stripped to match UriString.host(), which strips a leading "www." via
        // Uri.baseHost. Lookups always arrive www-stripped, so entities stored as "www.foo.com"
        // must live at key "foo.com" or they'd never be hit.
        val key = entity.domain.removePrefix("www.")
        val compiledRules = entity.rules.map { rule ->
            val regex = runCatching { ".*${rule.rule}.*".toRegex() }
                .onFailure { logcat(tag = "TrackerAllowlistRepository") { "Rule failed to compile, skipping: ${rule.rule} (${it.message})" } }
                .getOrNull()
            CompiledRule(rule = rule, regex = regex)
        }
        map.merge(key, compiledRules) { existing, new -> existing + new }
    }
    return map
}
