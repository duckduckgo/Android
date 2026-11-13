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

package com.duckduckgo.request.interception.impl

import android.net.Uri
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.TrackerAllowlist
import com.duckduckgo.request.interception.api.RequestBlocklist
import logcat.logcat
import javax.inject.Inject

enum class BlocklistVerdict { NOT_LISTED, BLOCKED, EXEMPTED }

class RequestExemptions @Inject constructor(
    private val requestBlocklist: RequestBlocklist,
    private val contentBlocking: ContentBlocking,
    private val trackerAllowlist: TrackerAllowlist,
    private val userAllowListRepository: UserAllowListRepository,
) {

    fun isTrusted(documentUri: Uri): Boolean = TrustedSites.isTrusted(documentUri)

    fun blocklistVerdict(
        isForMainFrame: Boolean,
        documentUri: Uri,
        url: Uri,
    ): BlocklistVerdict {
        if (isForMainFrame || !requestBlocklist.containedInBlocklist(documentUri, url)) return BlocklistVerdict.NOT_LISTED

        val documentUrlString = documentUri.toString()
        val isContentBlockingException = contentBlocking.isAnException(documentUrlString)
        val isInTrackerAllowList = trackerAllowlist.isAnException(documentUrlString, url.toString())
        val isUserAllowlisted = userAllowListRepository.isUriInUserAllowList(documentUri)

        if (!isContentBlockingException && !isInTrackerAllowList && !isUserAllowlisted) {
            logcat { "Blocking request $url by request blocklist" }
            return BlocklistVerdict.BLOCKED
        }
        return BlocklistVerdict.EXEMPTED
    }
}
