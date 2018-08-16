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
import com.duckduckgo.app.httpsupgrade.api.HttpsBloomFilterFactory
import com.duckduckgo.app.httpsupgrade.db.HttpsWhitelistDao
import timber.log.Timber
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

interface HttpsUpgrader {

    @WorkerThread
    fun shouldUpgrade(uri: Uri): Boolean

    fun upgrade(uri: Uri): Uri {
        return uri.buildUpon().scheme(UrlScheme.https).build()
    }

    fun reloadData()
}

class HttpsUpgraderImpl(
    private val whitelistedDao: HttpsWhitelistDao,
    private val bloomFactory: HttpsBloomFilterFactory
) : HttpsUpgrader {

    private var httpsBloomFilter: BloomFilter? = null
    private val dataReloadLock = ReentrantLock()

    init {
        thread {
            reloadData()
        }
    }

    @WorkerThread
    override fun shouldUpgrade(uri: Uri): Boolean {

        if (uri.isHttps) {
            return false
        }

        waitForAnyReloadsToComplete()

        val host = uri.host
        if (whitelistedDao.contains(host)) {
            Timber.d("${host} is in whitelist and so not upgradable")
            return false
        }

        httpsBloomFilter?.let {

            val initialTime = System.nanoTime()
            val shouldUpgrade = it.contains(host)
            val totalTime = System.nanoTime() - initialTime
            Timber.d("${host} ${if (shouldUpgrade) "is" else "is not"} upgradable, lookup in ${totalTime/NANO_TO_MILLIS_DIVISOR}ms")

            return shouldUpgrade
        }

        return false
    }

    private fun waitForAnyReloadsToComplete() {
        // wait for lock (by locking and unlocking) before continuing
        if (dataReloadLock.isLocked) {
            dataReloadLock.lock()
            dataReloadLock.unlock()
        }
    }

    override fun reloadData() {
        dataReloadLock.lock()
        httpsBloomFilter = bloomFactory.create()
        dataReloadLock.unlock()
    }

    companion object {
        const val NANO_TO_MILLIS_DIVISOR = 1_000_000.0
    }

}
