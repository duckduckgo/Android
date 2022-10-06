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

package com.duckduckgo.cookies.impl.features.firstparty

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.cookies.api.CookiesFeatureName
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.DAYS
import javax.inject.Inject

@ContributesWorker(AppScope::class)
class FirstPartyCookiesModifierWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {
    @Inject
    lateinit var firstPartyCookiesModifier: FirstPartyCookiesModifier
    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    override suspend fun doWork(): Result {
        return withContext(dispatcherProvider.io()) {
            val result = firstPartyCookiesModifier.expireFirstPartyCookies()
            return@withContext if (result) {
                Result.success()
            } else {
                Result.retry()
            }
        }
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = LifecycleObserver::class
)
@SingleInstanceIn(AppScope::class)
class FirstPartyCookiesModifierWorkerScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val toggle: FeatureToggle
) : DefaultLifecycleObserver {

    private val workerRequest = PeriodicWorkRequestBuilder<FirstPartyCookiesModifierWorker>(1, DAYS)
        .addTag(FIRST_PARTY_COOKIES_EXPIRE_WORKER_TAG)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
        .build()

    override fun onStop(owner: LifecycleOwner) {
        if (isFeatureEnabled()) {
            workManager.enqueueUniquePeriodicWork(FIRST_PARTY_COOKIES_EXPIRE_WORKER_TAG, ExistingPeriodicWorkPolicy.REPLACE, workerRequest)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        if (isFeatureEnabled()) {
            workManager.enqueueUniquePeriodicWork(FIRST_PARTY_COOKIES_EXPIRE_WORKER_TAG, ExistingPeriodicWorkPolicy.KEEP, workerRequest)
        }
    }

    private fun isFeatureEnabled(): Boolean = toggle.isFeatureEnabled(CookiesFeatureName.Cookie.value)

    companion object {
        private const val FIRST_PARTY_COOKIES_EXPIRE_WORKER_TAG = "FIRST_PARTY_COOKIES_EXPIRE_WORKER_TAG"
    }
}
