/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.auth2

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.subscriptions.impl.AccessTokenResult.Success
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.auth2.TokenRefreshWorker.Companion.REFRESH_WORKER_NAME
import com.squareup.anvil.annotations.ContributesBinding
import java.time.Duration
import java.util.concurrent.TimeUnit.MINUTES
import javax.inject.Inject

/**
 * The refresh token is valid for 1 month. We should ensure it doesn’t expire, as that could result in the user being signed out.
 * In practice, if the app is actively used, tokens will be refreshed more frequently since the access token is only valid for 4 hours.
 * Otherwise, this worker ensures that we obtain a new refresh token before the current one expires.
 */
interface BackgroundTokenRefresh {
    fun schedule()
}

@ContributesBinding(AppScope::class)
class BackgroundTokenRefreshImpl @Inject constructor(
    val workManager: WorkManager,
) : BackgroundTokenRefresh {
    override fun schedule() {
        val workRequest = PeriodicWorkRequestBuilder<TokenRefreshWorker>(repeatInterval = Duration.ofDays(7))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setInitialDelay(duration = Duration.ofDays(7))
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            REFRESH_WORKER_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest,
        )
    }
}

@ContributesWorker(AppScope::class)
class TokenRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    @Inject
    lateinit var workManager: WorkManager

    @Inject
    lateinit var subscriptionsManager: SubscriptionsManager

    override suspend fun doWork(): Result {
        return try {
            if (subscriptionsManager.isSignedInV2()) {
                /*
                    Access tokens are valid for only a few hours.
                    Calling getAccessToken() will refresh the tokens if they haven’t been refreshed recently.
                 */
                val result = subscriptionsManager.getAccessToken()
                check(result is Success)
            } else {
                workManager.cancelUniqueWork(REFRESH_WORKER_NAME)
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val REFRESH_WORKER_NAME = "WORKER_TOKEN_REFRESH"
    }
}
