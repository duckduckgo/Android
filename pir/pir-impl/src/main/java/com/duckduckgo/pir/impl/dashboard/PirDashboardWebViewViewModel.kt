/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.dashboard

import android.annotation.SuppressLint
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.codegen.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.pir.impl.PirUserUtils
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreeScanEntryPoint
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreeScanEntryPoint.FREE_SCAN_BANNER
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreeScanEntryPoint.SCAN_COMPLETE_NOTIFICATION
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreeScanEntryPoint.VIEW_RESULTS_BANNER
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreemiumDashboardScreens
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreemiumDashboardScreens.FromEntryPoint
import com.duckduckgo.pir.impl.dashboard.entrypoint.PirFreemiumDashboardScreens.FromRemoteMessage
import com.duckduckgo.pir.impl.dashboard.entrypoint.freeScanOrigin
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebMessages
import com.duckduckgo.pir.impl.dashboard.purchase.PirPurchaseRoute.NativePurchaseFlow
import com.duckduckgo.pir.impl.notifications.PirNotificationManager
import com.duckduckgo.pir.impl.pixels.PirInteractionReporter
import com.duckduckgo.pir.impl.pixels.PirNotificationPromptType
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.scan.PirForegroundScanServiceStarter
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.subscriptions.api.SubscriptionStatus.UNKNOWN
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

@SuppressLint("NoLifecycleObserver") // we don't observe app lifecycle
@ContributesViewModel(ActivityScope::class)
class PirDashboardWebViewViewModel @Inject constructor(
    private val pirPixelSender: PirPixelSender,
    private val pirInteractionReporter: PirInteractionReporter,
    private val pirUserUtils: PirUserUtils,
    private val appBuildConfig: AppBuildConfig,
    private val pirRepository: PirRepository,
    private val subscriptions: Subscriptions,
    private val pirFreemiumDataStore: PirFreemiumDataStore,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val foregroundScanServiceStarter: PirForegroundScanServiceStarter,
    private val pirNotificationManager: PirNotificationManager,
) : ViewModel(), DefaultLifecycleObserver {

    private var purchaseFlowOutstanding = false
    private var awaitingReturnFromNotificationSettings = false
    private var shouldShowRationaleBeforeRequest = false

    private val command = Channel<Command>(Channel.BUFFERED)
    internal fun commands(): Flow<Command> = command.receiveAsFlow()

    private val freeScanEntryPointResult = CompletableDeferred<PirFreeScanEntryPoint?>()
    private var isFreeScanEntryPointRequested = false

    /**
     * Where a free scan user opened this dashboard from. It's resolved once, when the dashboard opens, so a scan that
     * finishes during the visit doesn't change which entry point the visit is attributed to.
     */
    val freeScanEntryPoint: Deferred<PirFreeScanEntryPoint?> = freeScanEntryPointResult

    var resolvedFreeScanEntryPoint: PirFreeScanEntryPoint? = null
        private set

    init {
        // The dashboard needs to be reloaded on purchase
        subscriptions.getSubscriptionStatusFlow()
            .map { it != UNKNOWN }
            .distinctUntilChanged()
            .drop(1)
            .onEach { command.send(Command.ReloadWebView) }
            .launchIn(viewModelScope)
    }

    fun handleJsMessage(
        featureName: String,
        method: String,
        id: String?,
        data: JSONObject?,
    ) {
        // only forwarded once onboarding has saved the profile and started the initial scan
        if (method == PirDashboardWebMessages.SAVE_PROFILE.messageName) {
            viewModelScope.launch {
                command.send(Command.CheckNotificationPermission)
            }
        }
    }

    fun onNotificationPermissionStateComputed(shouldShowRationale: Boolean) {
        if (pirNotificationManager.canShowNotifications()) return

        shouldShowRationaleBeforeRequest = shouldShowRationale
        // with the permission granted, notifications are off for the app or the PIR channel, which only settings can change
        val promptType = if (pirNotificationManager.hasNotificationPermission()) {
            PirNotificationPromptType.OPEN_SETTINGS
        } else {
            PirNotificationPromptType.REQUEST_PERMISSION
        }
        showNotificationPermissionPrompt(promptType)
    }

    private fun showNotificationPermissionPrompt(promptType: PirNotificationPromptType) {
        viewModelScope.launch {
            command.send(Command.ShowNotificationPermissionPrompt(promptType))
        }
    }

    fun onNotificationPermissionPromptShown(promptType: PirNotificationPromptType) {
        pirPixelSender.reportNotificationPermissionPromptShown(promptType)
    }

    fun onNotificationPermissionPromptAccepted(promptType: PirNotificationPromptType) {
        pirPixelSender.reportNotificationPermissionPromptAccepted(promptType)

        viewModelScope.launch {
            when (promptType) {
                PirNotificationPromptType.REQUEST_PERMISSION -> command.send(Command.RequestNotificationPermission)
                PirNotificationPromptType.OPEN_SETTINGS -> {
                    awaitingReturnFromNotificationSettings = true
                    command.send(Command.OpenNotificationSettings)
                }
            }
        }
    }

    fun onNotificationPermissionPromptDismissed(promptType: PirNotificationPromptType) {
        pirPixelSender.reportNotificationPermissionPromptDismissed(promptType)
    }

    fun onNotificationPermissionResult(
        granted: Boolean,
        shouldShowRationale: Boolean,
    ) {
        if (granted) {
            pirPixelSender.reportNotificationPermissionGranted(PirNotificationPromptType.REQUEST_PERMISSION)
            showScanNotificationIfRunning()
            return
        }

        // no rationale before or after means the system answered without showing its dialog, so only settings can turn notifications on
        if (!shouldShowRationaleBeforeRequest && !shouldShowRationale) {
            showNotificationPermissionPrompt(PirNotificationPromptType.OPEN_SETTINGS)
        }
    }

    fun onResumed() {
        if (!awaitingReturnFromNotificationSettings) return
        awaitingReturnFromNotificationSettings = false

        if (pirNotificationManager.canShowNotifications()) {
            pirPixelSender.reportNotificationPermissionGranted(PirNotificationPromptType.OPEN_SETTINGS)
            showScanNotificationIfRunning()
        }
    }

    // the scan started before the permission was granted, so its notification was never shown
    private fun showScanNotificationIfRunning() {
        viewModelScope.launch(dispatcherProvider.io()) {
            foregroundScanServiceStarter.showScanNotificationIfRunning()
        }
    }

    fun onSubscriptionPurchaseRequested(route: NativePurchaseFlow) {
        // Prevent duplicate taps on the CTA and navigation
        if (purchaseFlowOutstanding) {
            return
        }
        purchaseFlowOutstanding = true

        viewModelScope.launch {
            command.send(
                Command.LaunchSubscriptionPurchase(
                    origin = freeScanOrigin(route.origin, freeScanEntryPointResult.await()),
                    featurePage = route.featurePage,
                ),
            )
        }
    }

    fun onLaunched(
        isRestored: Boolean,
        fromFreemiumScanCompleteNotification: Boolean,
        freemiumLaunch: PirFreemiumDashboardScreens?,
    ) {
        resolveFreeScanEntryPoint(freemiumLaunch, fromFreemiumScanCompleteNotification)

        // a restored dashboard keeps its launch intent, and the tap was already counted when it was first created
        if (isRestored || !fromFreemiumScanCompleteNotification) {
            return
        }

        pirPixelSender.reportFreemiumScanCompleteNotificationOpened()
    }

    override fun onCleared() {
        // the handshake waits on the entry point from the app scope, so it must not wait on a cleared ViewModel forever
        freeScanEntryPointResult.cancel()
        super.onCleared()
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        purchaseFlowOutstanding = false
        appCoroutineScope.launch {
            // read once so the open count and the interaction pixels never disagree on the user's tier
            val isAuthenticated = pirUserUtils.hasPirSubscription()
            pirPixelSender.reportDashboardOpened(isAuthenticated)
            pirInteractionReporter.attemptFirePixel(isAuthenticated)
        }

        if (appBuildConfig.isInternalBuild()) {
            viewModelScope.launch {
                command.send(
                    Command.ShowManualConfigWarning(pirRepository.hasBrokerConfigBeenManuallyUpdated()),
                )
            }
        }
    }

    private fun resolveFreeScanEntryPoint(
        freemiumLaunch: PirFreemiumDashboardScreens?,
        fromFreemiumScanCompleteNotification: Boolean,
    ) {
        // the ViewModel survives a configuration change, and the visit keeps the entry point it was opened with
        if (isFreeScanEntryPointRequested) return
        isFreeScanEntryPointRequested = true

        viewModelScope.launch {
            val entryPoint = when (freemiumLaunch) {
                is FromEntryPoint -> freemiumLaunch.entryPoint
                FromRemoteMessage -> withContext(dispatcherProvider.io()) { remoteMessageBannerEntryPoint() }
                null -> if (fromFreemiumScanCompleteNotification) SCAN_COMPLETE_NOTIFICATION else null
            }
            resolvedFreeScanEntryPoint = entryPoint
            freeScanEntryPointResult.complete(entryPoint)
        }
    }

    private fun remoteMessageBannerEntryPoint(): PirFreeScanEntryPoint =
        if (pirFreemiumDataStore.firstScanResult == null) FREE_SCAN_BANNER else VIEW_RESULTS_BANNER

    sealed class Command {
        data class SendJsEvent(val event: SubscriptionEventData) : Command()
        data class SendResponseToJs(val data: JsCallbackData) : Command()
        data class ShowManualConfigWarning(val show: Boolean) : Command()
        data class LaunchSubscriptionPurchase(
            val origin: String,
            val featurePage: String,
        ) : Command()

        data object ReloadWebView : Command()
        data object CheckNotificationPermission : Command()
        data class ShowNotificationPermissionPrompt(val promptType: PirNotificationPromptType) : Command()
        data object RequestNotificationPermission : Command()
        data object OpenNotificationSettings : Command()
    }
}
