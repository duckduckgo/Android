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
import com.duckduckgo.app.global.sha1
import com.duckduckgo.app.global.toHttps
import com.duckduckgo.app.httpsupgrade.api.HttpsBloomFilterFactory
import com.duckduckgo.app.httpsupgrade.api.HttpsUpgradeService
import com.duckduckgo.app.httpsupgrade.db.HttpsWhitelistDao
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.*
import timber.log.Timber
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

        if (whitelistedDao.contains(host)) {
            pixel.fire(HTTPS_NO_LOOKUP)
            Timber.d("$host is in whitelist and so not upgradable")
            return false
        }

        val isLocallyUpgradable = !isLocalListReloading && isInLocalUpgradeList(host)
        Timber.d("$host ${if (isLocallyUpgradable) "is" else "is not"} locally upgradable")
        if (isLocallyUpgradable) {
            pixel.fire(HTTPS_LOCAL_UPGRADE)
            return true
        }

        val serviceUpgradeResult = isInServiceUpgradeList(host)
        if (serviceUpgradeResult.isUpgradable) {
            pixel.fire(if (serviceUpgradeResult.isCached) HTTPS_SERVICE_CACHE_UPGRADE else HTTPS_SERVICE_REQUEST_UPGRADE)
        } else {
            pixel.fire(if (serviceUpgradeResult.isCached) HTTPS_SERVICE_CACHE_NO_UPGRADE else HTTPS_SERVICE_REQUEST_NO_UPGRADE)
        }
        Timber.d("$host ${if (serviceUpgradeResult.isUpgradable) "is" else "is not"} service upgradable")
        return serviceUpgradeResult.isUpgradable
    }

    @WorkerThread
    private fun isInLocalUpgradeList(host: String): Boolean {
        return localBloomFilter?.contains(host) == true
    }

    @WorkerThread
    private fun isInServiceUpgradeList(host: String): HttpsServiceResult {

        val sha1Host = host.sha1
        val partialSha1Host = sha1Host.substring(0, 4)

        try {
            val response = httpsUpgradeService.upgradeListForPartialHost(partialSha1Host).execute()
            if (response.isSuccessful) {
                val shouldUpgrade = response.body()?.contains(sha1Host) == true
                return HttpsServiceResult(shouldUpgrade, response.isCached)
            } else {
                Timber.w("Service https lookup failed with ${response.code()}")
            }
        } catch (error: Exception) {
            Timber.w("Service https lookup failed with $error")
        }

        return HttpsServiceResult(isUpgradable = false, isCached = false)
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

    data class HttpsServiceResult(
        val isUpgradable: Boolean,
        val isCached: Boolean
    )
}
