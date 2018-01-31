/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.httpsupgrade

import android.net.Uri
import android.support.annotation.WorkerThread
import com.duckduckgo.app.global.UrlScheme
import com.duckduckgo.app.global.isHttps
import com.duckduckgo.app.httpsupgrade.db.HttpsUpgradeDomainDao
import timber.log.Timber

interface HttpsUpgrader {

    @WorkerThread
    fun shouldUpgrade(uri: Uri) : Boolean

    fun upgrade(uri: Uri): Uri {
        return uri.buildUpon().scheme(UrlScheme.https).build()
    }
}

class HttpsUpgraderImpl constructor(private val dao: HttpsUpgradeDomainDao) :HttpsUpgrader {

    @WorkerThread
    override fun shouldUpgrade(uri: Uri) : Boolean {
        if (uri.isHttps) {
            return false
        }

        val host = (uri.host ?: return false).toLowerCase()
        return dao.hasDomain(host) || matchesWildcard(host)
    }

    private fun matchesWildcard(host: String): Boolean {
        val domains = mutableListOf<String>()
        for (part in host.split(".").reversed()) {
            if (domains.isEmpty()) {
                domains.add(".$part")
            } else {
                val last = domains.last()
                domains.add(".$part$last")
            }
        }

        domains.asReversed().removeAt(0)
        Timber.d("domains $domains")

        for (domain in domains) {
            if (dao.hasDomain("*$domain")) {
                return true
            }
        }

        return false
    }

}