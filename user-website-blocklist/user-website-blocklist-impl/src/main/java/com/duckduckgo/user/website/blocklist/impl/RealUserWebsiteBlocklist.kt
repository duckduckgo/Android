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

package com.duckduckgo.user.website.blocklist.impl

import android.net.Uri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.request.interception.api.RequestBlockerPlugin
import com.duckduckgo.request.interception.api.RequestBlockerRequest
import com.duckduckgo.user.website.blocklist.api.BlockedSite
import com.duckduckgo.user.website.blocklist.api.UserWebsiteBlocklist
import com.duckduckgo.user.website.blocklist.impl.db.UserBlockedWebsiteEntity
import com.duckduckgo.user.website.blocklist.impl.db.UserBlockedWebsitesDao
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, UserWebsiteBlocklist::class)
@ContributesMultibinding(AppScope::class, RequestBlockerPlugin::class)
class RealUserWebsiteBlocklist @Inject constructor(
    private val dao: UserBlockedWebsitesDao,
    private val feature: UserWebsiteBlocklistFeature,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    @IsMainProcess private val isMainProcess: Boolean,
) : UserWebsiteBlocklist, RequestBlockerPlugin {

    @Volatile
    private var cache: Set<String> = emptySet()

    init {
        if (isMainProcess) {
            appCoroutineScope.launch(dispatchers.io()) {
                reloadCache()
            }
        }
    }

    private fun reloadCache() {
        cache = dao.getAll().map { it.domain }.toSet()
    }

    override suspend fun block(url: Uri) {
        val domain = currentDomainOrNull(url) ?: return
        withContext(dispatchers.io()) {
            dao.insert(UserBlockedWebsiteEntity(domain = domain, addedAt = System.currentTimeMillis()))
            reloadCache()
        }
    }

    override suspend fun unblock(domain: String) {
        withContext(dispatchers.io()) {
            dao.delete(domain)
            reloadCache()
        }
    }

    override suspend fun clearAll() {
        withContext(dispatchers.io()) {
            dao.clear()
            reloadCache()
        }
    }

    override fun isBlocked(domain: String): Boolean = cache.contains(domain)

    override fun isBlocked(url: Uri): Boolean {
        val domain = currentDomainOrNull(url) ?: return false
        return isBlocked(domain)
    }

    override fun blockedDomainsWithTimestamps(): Flow<List<BlockedSite>> =
        dao.getAllAsFlow().map { rows -> rows.map { BlockedSite(it.domain, it.addedAt) } }

    override fun currentDomainOrNull(url: Uri): String? {
        val raw = url.toString()
        val httpUrl = raw.toHttpUrlOrNull() ?: "https://$raw".toHttpUrlOrNull() ?: return null
        return httpUrl.topPrivateDomain()
    }

    override suspend fun evaluate(request: RequestBlockerRequest): RequestBlockerPlugin.Decision {
        if (!request.isForMainFrame) return RequestBlockerPlugin.Decision.Ignore
        if (!feature.self().isEnabled()) return RequestBlockerPlugin.Decision.Ignore
        val domain = currentDomainOrNull(request.url) ?: return RequestBlockerPlugin.Decision.Ignore
        return if (cache.contains(domain)) {
            RequestBlockerPlugin.Decision.Block(RequestBlockerPlugin.BlockReason.UserBlocked(domain))
        } else {
            RequestBlockerPlugin.Decision.Ignore
        }
    }
}
