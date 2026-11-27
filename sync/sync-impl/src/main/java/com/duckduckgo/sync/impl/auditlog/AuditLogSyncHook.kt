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

package com.duckduckgo.sync.impl.auditlog

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import logcat.LogPriority.WARN
import logcat.logcat
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

interface AuditLogSyncHook {
    /**
     * Starts an audit log check in the background and returns at once. The same job then posts the new login notification.
     * Skips it if a check is already running.
     */
    fun onSyncSucceeded()

    /** Suspends until the check started by [onSyncSucceeded] and its notification end. Returns at once if none is running. */
    suspend fun awaitRunningCheck()
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAuditLogSyncHook @Inject constructor(
    private val checker: AuditLogChecker,
    private val newLoginNotifier: NewLoginNotifier,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : AuditLogSyncHook {

    private val lock = Any()
    private var runningCheck: Job? = null

    override fun onSyncSucceeded() {
        synchronized(lock) {
            if (runningCheck?.isCompleted == false) return

            runningCheck = appCoroutineScope.launch(dispatchers.io()) {
                try {
                    newLoginNotifier.onCheckFinished(checker.check())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(WARN) { "Sync-AuditLog: check threw ${e.javaClass.simpleName}" }
                }
            }
        }
    }

    override suspend fun awaitRunningCheck() {
        synchronized(lock) { runningCheck }?.join()
    }
}
