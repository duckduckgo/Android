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

/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.feature.removal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.ReceiverScope
import com.duckduckgo.mobile.android.vpn.feature.*
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService.Companion
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldOnboardingStore
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

// class VpnFeatureRemoverReceiver(
//     val context: Context,
//     val receiver: (Intent) -> Unit
// ) : BroadcastReceiver() {
//
//     override fun onReceive(
//         context: Context,
//         intent: Intent
//     ) {
//         receiver(intent)
//     }
//
//     fun register() {
//         unregister()
//         context.registerReceiver(this, IntentFilter(REMOVE_FEATURE))
//     }
//
//     fun unregister() {
//         kotlin.runCatching { context.unregisterReceiver(this) }
//     }
//
//     companion object {
//
//         private const val REMOVE_FEATURE = "remove-feature"
//
//         fun removeFeatureIntent(): Intent {
//             return Intent(REMOVE_FEATURE)
//         }
//
//         fun isRemoveFeatureIntent(intent: Intent): Boolean {
//             return intent.action == REMOVE_FEATURE
//         }
//
//     }
// }

@InjectWith(ReceiverScope::class)
class VpnFeatureRemoverReceiver : BroadcastReceiver() {

    @Inject
    lateinit var deviceShieldOnboardingStore: DeviceShieldOnboardingStore

    @Inject
    lateinit var featureRemover: VpnFeatureRemover

    @Inject
    lateinit var dispatcherProvider: DispatcherProvider

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        AndroidInjection.inject(this, context)

        Timber.i("VpnFeatureRemoverReceiver onReceive ${intent.action}")
        val pendingResult = goAsync()


        if (intent.action == ACTION_VPN_REMOVE_FEATURE) {
            Timber.v("VpnFeatureRemoverReceiver will remove the feature because the user asked it")
            deviceShieldOnboardingStore.removeVPNFeature()
            featureRemover.manuallyRemoveFeature()
            goAsync(pendingResult) {
                TrackerBlockingVpnService.stopService(context)
            }
        } else {
            Timber.w("VpnReminderReceiver: unknown action")
            pendingResult?.finish()
        }
    }

    companion object {
        const val ACTION_VPN_REMOVE_FEATURE = "com.duckduckgo.vpn.feature.remove"
    }

    fun goAsync(
        pendingResult: BroadcastReceiver.PendingResult?,
        coroutineScope: CoroutineScope = GlobalScope,
        block: suspend () -> Unit
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                block()
            } finally {
                // Always call finish(), even if the coroutineScope was cancelled
                pendingResult?.finish()
            }
        }
    }
}

// @ContributesMultibinding(AppScope::class)
// class VpnRemoveFeatureReceiverRegister @Inject constructor(
//     private val context: Context,
//     private val deviceShieldOnboardingStore: DeviceShieldOnboardingStore,
//     private val featureRemover: VpnFeatureRemover,
//     private val dispatcherProvider: DispatcherProvider,
// ) : VpnServiceCallbacks {
//
//     private var receiver: VpnFeatureRemoverReceiver? = null
//
//     override fun onVpnStarted(coroutineScope: CoroutineScope) {
//         Timber.v("Receiver VpnFeatureRemoverReceiver registered")
//
//         receiver = VpnFeatureRemoverReceiver(context) { intent ->
//             Timber.v("VpnFeatureRemoverReceiver receive $intent")
//             when {
//                 VpnFeatureRemoverReceiver.isRemoveFeatureIntent(intent) -> {
//                     Timber.v("VpnFeatureRemoverReceiver removing Vpn Feature")
//                     coroutineScope.launch {
//                         deviceShieldOnboardingStore.removeVPNFeature()
//                         featureRemover.manuallyRemoveFeature()
//                         withContext(dispatcherProvider.main()) {
//                             TrackerBlockingVpnService.stopService(context)
//                             receiver?.unregister()
//                         }
//                     }
//                 }
//                 else -> Timber.w("RemoteFeatureReceiver unknown intent")
//             }
//         }.apply { register() }
//     }
//
//     override fun onVpnStopped(
//         coroutineScope: CoroutineScope,
//         vpnStopReason: VpnStopReason
//     ) {
//         // no-op
//     }
// }
