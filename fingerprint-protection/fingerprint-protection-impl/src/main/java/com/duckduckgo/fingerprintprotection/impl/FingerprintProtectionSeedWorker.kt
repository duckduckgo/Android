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

package com.duckduckgo.fingerprintprotection.impl

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.anvil.annotations.ContributesWorker
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.fingerprintprotection.store.seed.FingerprintProtectionSeedRepository
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.DAYS
import javax.inject.Inject
import kotlinx.coroutines.withContext

@ContributesWorker(AppScope::class)
class FingerprintProtectionSeedWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    @Inject
    lateinit var fingerprintProtectionSeedRepository: FingerprintProtectionSeedRepository

    override suspend fun doWork(): Result {
        return withContext(dispatcherProvider.io()) {
            fingerprintProtectionSeedRepository.storeNewSeed()
            return@withContext Result.success()
        }
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class FingerprintProtectionSeedWorkerScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val fingerprintProtectionSeedRepository: FingerprintProtectionSeedRepository,
) : MainProcessLifecycleObserver {

    private val workerRequest = PeriodicWorkRequestBuilder<FingerprintProtectionSeedWorker>(1, DAYS)
        .addTag(FINGERPRINT_PROTECTION_SEED_WORKER_TAG)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
        .build()

    override fun onStop(owner: LifecycleOwner) {
        fingerprintProtectionSeedRepository.storeNewSeed()
        workManager.enqueueUniquePeriodicWork(FINGERPRINT_PROTECTION_SEED_WORKER_TAG, ExistingPeriodicWorkPolicy.REPLACE, workerRequest)
    }

    override fun onStart(owner: LifecycleOwner) {
        workManager.enqueueUniquePeriodicWork(FINGERPRINT_PROTECTION_SEED_WORKER_TAG, ExistingPeriodicWorkPolicy.KEEP, workerRequest)
    }

    companion object {
        private const val FINGERPRINT_PROTECTION_SEED_WORKER_TAG = "FINGERPRINT_PROTECTION_SEED_WORKER_TAG"
    }
}
