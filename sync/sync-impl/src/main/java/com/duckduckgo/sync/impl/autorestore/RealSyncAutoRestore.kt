/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.autorestore

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.SyncAutoRestore
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = SyncAutoRestore::class)
@ContributesBinding(AppScope::class, boundType = SyncOnboardingRestoreState::class)
class RealSyncAutoRestore @Inject constructor(
    private val manager: SyncAutoRestoreManager,
    private val syncFeature: SyncFeature,
    private val syncAccountRepository: SyncAccountRepository,
    private val syncPixels: SyncPixels,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : SyncAutoRestore, SyncOnboardingRestoreState {

    @Volatile private var restoreTimestamp: Long? = null

    override suspend fun canRestore(): Boolean {
        return withContext(dispatcherProvider.io()) {
            if (!syncFeature.syncAutoRestore().isEnabled()) return@withContext false
            manager.retrieveRecoveryPayload() != null
        }
    }

    override fun restoreSyncAccount() {
        appScope.launch(dispatcherProvider.io()) {
            try {
                if (!syncFeature.syncAutoRestore().isEnabled()) {
                    logcat(LogPriority.WARN) { "Sync-Recovery: syncAutoRestore FF disabled, skipping restore" }
                    return@launch
                }
                logcat { "Sync-Recovery: restoreSyncAccount called" }

                val payload = manager.retrieveRecoveryPayload()
                if (payload == null) {
                    logcat(LogPriority.WARN) { "Sync-Recovery: no recovery key found in persistent storage" }
                    return@launch
                }

                logcat { "Sync-Recovery: recovery key retrieved, attempting login" }

                val parsedCode = syncAccountRepository.parseSyncAuthCode(payload.recoveryCode)
                when (val result = syncAccountRepository.processCode(parsedCode, existingDeviceId = payload.deviceId)) {
                    is Result.Success -> {
                        logcat(LogPriority.INFO) { "Sync-Recovery: account restored successfully" }
                        restoreTimestamp = System.currentTimeMillis()
                        syncPixels.fireAutoRestoreSuccess(SyncPixelParameters.AUTO_RESTORE_SOURCE_ONBOARDING)
                        manager.saveAutoRestoreData(payload.recoveryCode, payload.deviceId)
                    }
                    is Result.Error -> {
                        logcat(LogPriority.WARN) { "Sync-Recovery: restore failed - code=${result.code}, reason=${result.reason}" }
                        syncPixels.fireAutoRestoreFailure(
                            source = SyncPixelParameters.AUTO_RESTORE_SOURCE_ONBOARDING,
                            errorCode = result.code.toString(),
                            errorMessage = result.reason,
                        )
                    }
                }
            } catch (t: Throwable) {
                coroutineContext.ensureActive()
                logcat(LogPriority.ERROR) { "Sync-Recovery: unexpected error during restore - ${t.message}" }
            }
        }
    }

    override fun autoRestoredTimestamp(): Long? = restoreTimestamp
}
