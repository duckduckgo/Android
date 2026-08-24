/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection

import android.net.Uri
import androidx.annotation.WorkerThread
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.trackerdetection.db.TdsCnameEntityDao
import com.duckduckgo.app.trackerdetection.flags.OptimizeCnameDetectionRCWrapper
import com.duckduckgo.common.utils.UrlScheme
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.TrackerAllowlist
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

interface CloakedCnameDetector {
    fun detectCnameCloakedHost(documentUrl: String?, url: Uri): String?
}

interface CloakedCnameRefresher {
    @WorkerThread
    fun refresh()
}

@ContributesBinding(AppScope::class, boundType = CloakedCnameDetector::class)
@ContributesBinding(AppScope::class, boundType = CloakedCnameRefresher::class)
@SingleInstanceIn(AppScope::class)
class CloakedCnameDetectorImpl @Inject constructor(
    private val tdsCnameEntityDao: TdsCnameEntityDao,
    private val trackerAllowlist: TrackerAllowlist,
    private val userAllowListRepository: UserAllowListRepository,
    private val optimizeCnameDetectionRCWrapper: OptimizeCnameDetectionRCWrapper,
) : CloakedCnameDetector, CloakedCnameRefresher {

    /**
     * The CNAME table is only rewritten when TDS is downloaded, so it is held in memory and
     * recomputed on [refresh] rather than queried per request.
     */
    @Volatile
    private var cnames: Map<String, String>? = null

    override fun refresh() {
        cnames = loadCnames()
    }

    override fun detectCnameCloakedHost(documentUrl: String?, url: Uri): String? {
        val host = url.host ?: return null
        val uncloakedHostName = uncloakedHostFor(host) ?: return null

        if (documentUrl != null && trackerAllowlist.isAnException(documentUrl, url.toString()) ||
            userAllowListRepository.isUriInUserAllowList(url)
        ) { return null }

        logcat(VERBOSE) { "$host is a CNAME cloaked host. Uncloaked host name: $uncloakedHostName" }

        val scheme = url.scheme ?: UrlScheme.http
        return "$scheme://$uncloakedHostName${url.path.orEmpty()}"
    }

    private fun uncloakedHostFor(host: String): String? {
        return if (optimizeCnameDetectionRCWrapper.enabled) {
            activeCnames()[host]
        } else {
            tdsCnameEntityDao.get(host)?.uncloakedHostName
        }
    }

    private fun activeCnames(): Map<String, String> {
        cnames?.let { return it }
        return synchronized(this) {
            cnames ?: loadCnames().also { cnames = it }
        }
    }

    @WorkerThread
    private fun loadCnames(): Map<String, String> =
        tdsCnameEntityDao.getAll().associate { it.cloakedHostName to it.uncloakedHostName }
}
