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
import com.duckduckgo.app.trackerdetection.db.TdsCnameEntityDao
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import timber.log.Timber
import javax.inject.Inject

interface CloakedCnameDetector {
    fun detectCnameCloakedHost(url: Uri): String?
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class CloakedCnameDetectorImpl @Inject constructor(
    private val tdsCnameEntityDao: TdsCnameEntityDao
) : CloakedCnameDetector {

    override fun detectCnameCloakedHost(url: Uri): String? {
        url.host?.let { host ->
            tdsCnameEntityDao.get(host)?.let { cnameEntity ->
                var uncloakedHostName = cnameEntity.uncloakedHostName
                Timber.v("$host is a CNAME cloaked host. Uncloaked host name: $uncloakedHostName")
                url.path?.let { path ->
                    uncloakedHostName += path
                }
                return uncloakedHostName
            }
        }
        return null
    }
}
