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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.duckduckgo.app.global.isHttps
import com.duckduckgo.app.global.toHttps
import com.duckduckgo.app.httpsupgrade.store.HttpsFalsePositivesDao
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.Https
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import timber.log.Timber
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import dagger.SingleInstanceIn
import kotlin.concurrent.thread

interface HttpsUpgrader {

    @WorkerThread
    fun shouldUpgrade(uri: Uri): Boolean

    fun upgrade(uri: Uri): Uri {
        return uri.toHttps
    }

    @WorkerThread
    fun reloadData()
}

@SingleInstanceIn(AppObjectGraph::class)
@ContributesBinding(
    scope = AppObjectGraph::class,
    boundType = HttpsUpgrader::class
)
class HttpsUpgraderImpl @Inject constructor(
    private val bloomFactory: HttpsBloomFilterFactory,
    private val bloomFalsePositiveDao: HttpsFalsePositivesDao,
    private val userAllowListDao: UserWhitelistDao,
    private val toggle: FeatureToggle,
    private val https: Https
) : HttpsUpgrader, LifecycleObserver {

    private var bloomFilter: BloomFilter? = null
    private val bloomReloadLock = ReentrantLock()

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onApplicationCreated() {
        thread { reloadData() }
    }

    @WorkerThread
    override fun shouldUpgrade(uri: Uri): Boolean {
        val host = uri.host ?: return false

        if (toggle.isFeatureEnabled(PrivacyFeatureName.HttpsFeatureName()) != true) {
            Timber.d("https is disabled in the remote config and so $host is not upgradable")
            return false
        }

        if (uri.isHttps) {
            return false
        }

        if (https.isAnException(uri.toString())) {
            Timber.d("$host is in the remote exception list and so not upgradable")
            return false
        }

        if (userAllowListDao.contains(host)) {
            Timber.d("$host is in user allowlist and so not upgradable")
            return false
        }

        if (bloomFalsePositiveDao.contains(host)) {
            Timber.d("$host is in https whitelist and so not upgradable")
            return false
        }

        val isUpgradable = isInUpgradeList(host)
        Timber.d("$host ${if (isUpgradable) "is" else "is not"} upgradable")
        return isUpgradable
    }

    @WorkerThread
    private fun isInUpgradeList(host: String): Boolean {
        waitForAnyReloadsToComplete()
        return bloomFilter?.contains(host) == true
    }

    @WorkerThread
    override fun reloadData() {
        Timber.v("Reload Https upgrader data")
        bloomReloadLock.lock()
        try {
            bloomFilter = bloomFactory.create()
        } finally {
            bloomReloadLock.unlock()
        }
    }

    private fun waitForAnyReloadsToComplete() {
        // wait for lock (by locking and unlocking) before continuing
        if (bloomReloadLock.isLocked) {
            bloomReloadLock.lock()
            bloomReloadLock.unlock()
        }
    }
}

@Module
@ContributesTo(AppObjectGraph::class)
abstract class HttpsUpgraderModule {

    @SingleInstanceIn(AppObjectGraph::class)
    @Binds
    @IntoSet
    abstract fun bindHttpsUpgraderLifecycleObserver(
        httpsUpgraderImpl: HttpsUpgraderImpl
    ): LifecycleObserver
}
