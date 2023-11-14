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
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.trackerdetection.db.TdsCnameEntityDao
import com.duckduckgo.common.utils.UrlScheme
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.TrackerAllowlist
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import timber.log.Timber

interface CloakedCnameDetector {
    fun detectCnameCloakedHost(documentUrl: String?, url: Uri): String?
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class CloakedCnameDetectorImpl @Inject constructor(
    private val tdsCnameEntityDao: TdsCnameEntityDao,
    private val trackerAllowlist: TrackerAllowlist,
    private val userAllowListRepository: UserAllowListRepository,
) : CloakedCnameDetector {

    override fun detectCnameCloakedHost(documentUrl: String?, url: Uri): String? {
        if (documentUrl != null && trackerAllowlist.isAnException(documentUrl, url.toString()) ||
            userAllowListRepository.isUriInUserAllowList(url)
        ) { return null }

        url.host?.let { host ->
            tdsCnameEntityDao.get(host)?.let { cnameEntity ->
                var uncloakedHostName = cnameEntity.uncloakedHostName
                Timber.v("$host is a CNAME cloaked host. Uncloaked host name: $uncloakedHostName")
                url.path?.let { path ->
                    uncloakedHostName += path
                }
                uncloakedHostName = if (url.scheme != null) {
                    "${url.scheme}://$uncloakedHostName"
                } else {
                    "${UrlScheme.http}://$uncloakedHostName"
                }
                return uncloakedHostName
            }
        }
        return null
    }
}
