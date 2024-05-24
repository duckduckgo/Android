/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.impl.RealSubscriptionsChecker.Companion.TAG_WORKER_SUBSCRIPTION_CHECK
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import java.util.concurrent.TimeUnit.HOURS
import java.util.concurrent.TimeUnit.MINUTES
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface SubscriptionsChecker {
    suspend fun runChecker()
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = SubscriptionsChecker::class,
)
class RealSubscriptionsChecker @Inject constructor(
    private val workManager: WorkManager,
    private val subscriptionsManager: SubscriptionsManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver, SubscriptionsChecker {
    override fun onStart(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            runChecker()
        }
    }

    override suspend fun runChecker() {
        if (subscriptionsManager.subscriptionStatus() != UNKNOWN) {
            PeriodicWorkRequestBuilder<SubscriptionsCheckWorker>(1, HOURS)
                .addTag(TAG_WORKER_SUBSCRIPTION_CHECK)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, MINUTES)
                .build().run {
                    workManager.enqueueUniquePeriodicWork(
                        TAG_WORKER_SUBSCRIPTION_CHECK,
                        ExistingPeriodicWorkPolicy.REPLACE,
                        this,
                    )
                }
        }
    }

    companion object {
        const val TAG_WORKER_SUBSCRIPTION_CHECK = "TAG_WORKER_SUBSCRIPTION_CHECK"
    }
}

@ContributesWorker(AppScope::class)
class SubscriptionsCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @Inject
    lateinit var subscriptionsManager: SubscriptionsManager

    @Inject
    lateinit var workManager: WorkManager

    override suspend fun doWork(): Result {
        return try {
            if (subscriptionsManager.subscriptionStatus() != UNKNOWN) {
                val subscription = subscriptionsManager.fetchAndStoreAllData()
                if (subscription?.status == null || subscription.status == UNKNOWN) {
                    workManager.cancelAllWorkByTag(TAG_WORKER_SUBSCRIPTION_CHECK)
                }
            } else {
                workManager.cancelAllWorkByTag(TAG_WORKER_SUBSCRIPTION_CHECK)
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
