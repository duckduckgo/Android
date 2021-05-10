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

package com.duckduckgo.mobile.android.vpn.blocklist

import androidx.annotation.WorkerThread
import com.duckduckgo.app.global.extensions.extractETag
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.trackers.AppTracker
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerExcludedPackage
import com.squareup.anvil.annotations.ContributesBinding
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import timber.log.Timber
import javax.inject.Inject

data class AppTrackerBlocklist(
    val etag: ETag = ETag.InvalidETag,
    val blocklist: List<AppTracker> = listOf()
)

data class AppTrackerExclusionList(
    val etag: ETag = ETag.InvalidETag,
    val excludedPackages: List<AppTrackerExcludedPackage> = listOf()
)

sealed class ETag {
    object InvalidETag : ETag()
    data class ValidETag(val value: String) : ETag()
}

interface AppTrackerListDownloader {
    @WorkerThread
    fun downloadAppTrackerBlocklist(): AppTrackerBlocklist

    @WorkerThread
    fun downloadAppTrackerExclusionList(): AppTrackerExclusionList
}

@ContributesBinding(AppObjectGraph::class)
class RealAppTrackerListDownloader @Inject constructor(
    private val appTrackerListService: AppTrackerListService
) : AppTrackerListDownloader {
    override fun downloadAppTrackerBlocklist(): AppTrackerBlocklist {
        Timber.d("Downloading the app tracker blocklist...")
        val response = runCatching {
            appTrackerListService.appTrackerBlocklist().execute()
        }.getOrDefault(Response.error(400, "".toResponseBody(null)))

        if (!response.isSuccessful) {
            Timber.e("Fail to download the app tracker blocklist, error code: ${response.code()}")
            return AppTrackerBlocklist()
        }

        val eTag = response.headers().extractETag()
        val blocklist = response.body()?.trackers.orEmpty()
            .filter { !it.value.isCdn }
            .mapValues {
                AppTracker(
                    hostname = it.key,
                    trackerCompanyId = it.value.owner.displayName.hashCode(),
                    owner = it.value.owner,
                    app = it.value.app,
                    isCdn = it.value.isCdn
                )
            }.map { it.value }

        Timber.d("Received the app tracker blocklist, size: ${blocklist.size}")

        return AppTrackerBlocklist(etag = ETag.ValidETag(eTag), blocklist = blocklist)
    }

    override fun downloadAppTrackerExclusionList(): AppTrackerExclusionList {
        Timber.d("Downloading the app tracker exclusion list...")
        val response = runCatching {
            appTrackerListService.appTrackerExclusionList().execute()
        }.getOrDefault(Response.error(400, "".toResponseBody(null)))

        if (!response.isSuccessful) {
            Timber.e("Fail to download the app tracker exclusion list, error code: ${response.code()}")
            return AppTrackerExclusionList()
        }

        val eTag = response.headers().extractETag()
        val exclusionList = response.body()?.rules.orEmpty()
            .map { AppTrackerExcludedPackage(it) }

        Timber.d("Received the app tracker exclusion list, size: ${exclusionList.size}")

        return AppTrackerExclusionList(etag = ETag.ValidETag(eTag), excludedPackages = exclusionList)
    }
}
