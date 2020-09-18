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
import com.duckduckgo.app.global.isHttps
import com.duckduckgo.app.global.toHttps
import com.duckduckgo.app.httpsupgrade.api.HttpsBloomFilterFactory
import com.duckduckgo.app.httpsupgrade.db.HttpsWhitelistDao
import com.duckduckgo.app.privacy.db.UserWhitelistDao
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
    private val bloomFactory: HttpsBloomFilterFactory,
    private val bloomFalsePositiveDao: HttpsWhitelistDao,
    private val userAllowListDao: UserWhitelistDao,
    private val pixel: Pixel
) : HttpsUpgrader {

    private var bloomFilter: BloomFilter? = null
    private val bloomReloadLock = ReentrantLock()
    private val isBloomReloading get() = bloomReloadLock.isLocked

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

        if (userAllowListDao.contains(host)) {
            pixel.fire(HTTPS_NO_LOOKUP)
            Timber.d("$host is in user allowlist and so not upgradable")
            return false
        }

        if (bloomFalsePositiveDao.contains(host)) {
            pixel.fire(HTTPS_NO_LOOKUP)
            Timber.d("$host is in https whitelist and so not upgradable")
            return false
        }

        val isUpgradable = isInUpgradeList(host)
        Timber.d("$host ${if (isUpgradable) "is" else "is not"} upgradable")
        pixel.fire(if (isUpgradable) HTTPS_LOCAL_UPGRADE else HTTPS_NO_UPGRADE)
        return isUpgradable
    }

    @WorkerThread
    private fun isInUpgradeList(host: String): Boolean {
        return bloomFilter?.contains(host) == true
    }

    @WorkerThread
    override fun reloadData() {
        bloomReloadLock.lock()
        try {
            bloomFilter = bloomFactory.create()
        } finally {
            bloomReloadLock.unlock()
        }
    }
}
