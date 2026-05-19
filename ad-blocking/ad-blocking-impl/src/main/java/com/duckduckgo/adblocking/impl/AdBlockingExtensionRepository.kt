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

import com.duckduckgo.adblocking.impl.store.AdBlockingExtensionDao
import com.duckduckgo.adblocking.impl.store.ScriptletEntity
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface AdBlockingExtensionRepository {
    suspend fun getStoredVersion(): String?
    suspend fun storeScriptlets(version: String, scriptlets: Map<String, ByteArray>)
    fun scriptletsFlow(): Flow<List<Scriptlet>>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAdBlockingExtensionRepository @Inject constructor(
    private val dao: AdBlockingExtensionDao,
) : AdBlockingExtensionRepository {

    override suspend fun getStoredVersion(): String? = dao.getVersion()

    override suspend fun storeScriptlets(version: String, scriptlets: Map<String, ByteArray>) {
        dao.replaceAll(
            version = version,
            scriptlets = scriptlets.map { (name, content) ->
                ScriptletEntity(name = name, content = content)
            },
        )
    }

    override fun scriptletsFlow(): Flow<List<Scriptlet>> =
        dao.scriptletsFlow().map { rows ->
            rows.map { row -> Scriptlet(name = row.name, content = String(row.content)) }
        }
}
