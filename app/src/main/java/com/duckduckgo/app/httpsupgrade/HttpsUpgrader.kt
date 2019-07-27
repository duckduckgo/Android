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
import androidx.annotation.WorkerThread
import com.duckduckgo.app.global.api.isCached
import com.duckduckgo.app.global.isHttps
import com.duckduckgo.app.global.performance.measureExecution
import com.duckduckgo.app.global.sha1
import com.duckduckgo.app.global.toHttps
import com.duckduckgo.app.httpsupgrade.api.HttpsBloomFilterFactory
import com.duckduckgo.app.httpsupgrade.api.HttpsUpgradeService
import com.duckduckgo.app.httpsupgrade.db.HttpsWhitelistDao
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.*
import timber.log.Timber
import java.lang.Error
import java.util.concurrent.locks.ReentrantLock

interface HttpsUpgrader {

    @WorkerThread
    fun shouldUpgrade(uri: Uri): Boolean

    fun upgrade(uri: Uri): Uri {
        return uri.toHttps
    }

    @WorkerThread
    fun reloadData()
}

class HttpsUpgraderImpl(
    private val whitelistedDao: HttpsWhitelistDao,
    private val bloomFactory: HttpsBloomFilterFactory,
    private val httpsUpgradeService: HttpsUpgradeService,
    private val pixel: Pixel
) : HttpsUpgrader {

    private var localBloomFilter: BloomFilter? = null
    private val localDataReloadLock = ReentrantLock()
    private val isLocalListReloading get() = localDataReloadLock.isLocked

    @WorkerThread
    override fun shouldUpgrade(uri: Uri): Boolean {

        if (uri.isHttps) {
            pixel.fire(HTTPS_NO_LOOKUP)
            return false
        }

        val host = uri.host
        if (host == null) {
            pixel.fire(HTTPS_NO_LOOKUP)
            return false
        }

        if (!isLocalListReloading && isInLocalUpgradeList(host)) {
            pixel.fire(HTTPS_LOCAL_LOOKUP)
            return true
        }

        return isInServiceUpgradeList(host)
    }

    @WorkerThread
    private fun isInLocalUpgradeList(host: String): Boolean {

        if (whitelistedDao.contains(host)) {
            Timber.d("$host is in whitelist and so not upgradable")
            return false
        }

        var shouldUpgrade = false

        localBloomFilter?.let {
            measureExecution("Local Https lookup took") {
                shouldUpgrade = it.contains(host)
            }
        }

        Timber.d("$host ${if (shouldUpgrade) "is" else "is not"} locally upgradable according to local filter")
        return shouldUpgrade
    }

    @WorkerThread
    private fun isInServiceUpgradeList(host: String): Boolean {

        var shouldUpgrade = false

        measureExecution("Service Https lookup took") {
            val sha1Host = host.sha1
            val partialSha1Host = sha1Host.substring(0, 4)

            try {
                val response = httpsUpgradeService.upgradeListForPartialHost(partialSha1Host).execute()
                if (response.isSuccessful) {
                    shouldUpgrade = response.body()?.contains(sha1Host) == true
                    pixel.fire(if (response.isCached) HTTPS_SERVICE_CACHE_LOOKUP else HTTPS_SERVICE_REQUEST_LOOKUP)
                } else {
                    Timber.w("Service https lookup failed with ${response.code()}")
                }

            } catch (error: Exception) {
                Timber.w("Service https lookup failed with $error")
            }
        }

        Timber.d("$host ${if (shouldUpgrade) "is" else "is not"} upgradable according to service")
        return false
    }

    @WorkerThread
    override fun reloadData() {
        localDataReloadLock.lock()
        try {
            localBloomFilter = bloomFactory.create()
        } finally {
            localDataReloadLock.unlock()
        }
    }
}
