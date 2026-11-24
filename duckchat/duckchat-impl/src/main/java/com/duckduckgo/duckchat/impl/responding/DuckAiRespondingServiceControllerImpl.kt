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

package com.duckduckgo.duckchat.impl.responding

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.duckduckgo.duckchat.impl.responding.RespondingServiceStartFailure.LATE_REQUEST_NOT_ALLOWED
import com.duckduckgo.duckchat.impl.responding.RespondingServiceStartFailure.REQUEST_FAILED
import com.duckduckgo.duckchat.impl.responding.RespondingServiceStartFailure.REQUEST_NOT_ALLOWED
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<DuckAiRespondingServiceController>())
@ContributesBinding(AppScope::class, binding = binding<RespondingServiceSupervisor>())
@ContributesIntoSet(AppScope::class, binding = binding<BrowserLifecycleObserver>())
@ContributesIntoSet(AppScope::class, binding = binding<MainProcessLifecycleObserver>())
class DuckAiRespondingServiceControllerImpl @Inject constructor(
    private val context: Context,
    private val runStore: DuckAiRespondingServiceRunStore,
    private val pixels: Lazy<DuckChatPixels>,
    private val dispatchers: DispatcherProvider,
    @param:AppCoroutineScope private val appScope: CoroutineScope,
) : DuckAiRespondingServiceController, RespondingServiceSupervisor, BrowserLifecycleObserver, MainProcessLifecycleObserver {

    @Volatile
    private var keepAliveOnLeave = false

    private var appOpen = false

    /**
     * The service gets one start attempt when user backgrounds the app, either in [onClose] or as a late start if [setKeepAliveOnLeave] is enabled after the app is already in the background.
     * Service start needs to happen within the [grace period for "app transitions from a user-visible state"](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start#background-start-restriction-exemptions),
     * so once it fails, later attempts would fail too and only add failure pixels.
     */
    private var lateStartAllowed = false

    private val runStoreDispatcher = dispatchers.io().limitedParallelism(1)

    private val _keepServiceRunning = MutableStateFlow(false)
    override val keepServiceRunning: StateFlow<Boolean> = _keepServiceRunning.asStateFlow()

    override fun setKeepAliveOnLeave(enabled: Boolean) {
        keepAliveOnLeave = enabled
        appScope.launch(dispatchers.main()) {
            updateKeepServiceRunning()
            if (!keepAliveOnLeave) {
                updateRunStore { clear() }
            } else if (!appOpen && lateStartAllowed) {
                tryStart(reportIfStartFails = LATE_REQUEST_NOT_ALLOWED)
            }
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        updateRunStore {
            takeInterruptedRun()?.let { pixels.value.reportRespondingServiceInterrupted(restricted = it.restricted) }
        }
    }

    override fun onClose() {
        appOpen = false
        lateStartAllowed = true
        updateKeepServiceRunning()
        if (keepAliveOnLeave) tryStart(reportIfStartFails = REQUEST_NOT_ALLOWED)
    }

    override fun onOpen(isFreshLaunch: Boolean) {
        appOpen = true
        updateKeepServiceRunning()
        updateRunStore { clear() }
    }

    override fun onStartFailed(failure: RespondingServiceStartFailure) {
        updateRunStore { clear() }
        pixels.value.reportRespondingServiceStartFailed(failure)
    }

    override fun onServiceTimedOut() {
        if (!keepServiceRunning.value) return
        updateRunStore { clear() }
        pixels.value.reportRespondingServiceTimedOut()
    }

    private fun updateKeepServiceRunning() {
        _keepServiceRunning.value = keepAliveOnLeave && !appOpen
    }

    private fun updateRunStore(block: DuckAiRespondingServiceRunStore.() -> Unit) {
        appScope.launch(runStoreDispatcher) { runStore.block() }
    }

    private fun tryStart(reportIfStartFails: RespondingServiceStartFailure) {
        lateStartAllowed = false
        val restricted = context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted ?: false
        updateRunStore { markRunning(restricted) }
        try {
            context.startForegroundService(Intent(context, DuckAiRespondingService::class.java))
        } catch (e: Exception) {
            onStartFailed(if (e is IllegalStateException) reportIfStartFails else REQUEST_FAILED)
            return
        }
        pixels.value.reportRespondingServiceStarted(restricted)
    }
}
