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
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.di.scopes.ReceiverScope
import com.duckduckgo.mobile.android.vpn.feature.*
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldOnboardingStore
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@InjectWith(ReceiverScope::class)
class VpnFeatureRemoverReceiver : BroadcastReceiver() {

    @Inject
    lateinit var deviceShieldOnboardingStore: DeviceShieldOnboardingStore

    @Inject
    lateinit var featureRemover: VpnFeatureRemover

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        AndroidInjection.inject(this, context)
        Timber.v("VpnFeatureRemoverReceiver onReceive $intent")
        val pendingResult = goAsync()

        if (intent.action == REMOVE_FEATURE) {
            Timber.v("VpnFeatureRemoverReceiver onReceive $intent")
            if (deviceShieldOnboardingStore.shouldRemoveVpnFeature()) {
                Timber.v("VpnFeatureRemoverReceiver removing Vpn Feature")
                goAsync(pendingResult) {
                    deviceShieldOnboardingStore.removeVPNFeature()
                    featureRemover.manuallyRemoveFeature()
                    deviceShieldOnboardingStore.forgetRemoveVpnFeature()
                    TrackerBlockingVpnService.stopService(context)
                }
            } else {
                Timber.v("VpnFeatureRemoverReceiver should not remove Vpn Feature")
            }
        }
    }

    companion object {

        const val REMOVE_FEATURE = "com.duckduckgo.mobile.android.vpn.feature-removal"

        fun removeFeatureIntent(): Intent {
            return Intent(REMOVE_FEATURE)
        }

        fun isRemoveFeatureIntent(intent: Intent): Boolean {
            return intent.action == REMOVE_FEATURE
        }
    }
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
//
// @ContributesMultibinding(AppScope::class)
// class VpnRemoveFeatureReceiverRegister @Inject constructor(
//     private val context: Context,
//     private val deviceShieldOnboardingStore: DeviceShieldOnboardingStore,
//     private val featureRemover: VpnFeatureRemover,
//     @AppCoroutineScope private val coroutineScope: CoroutineScope
// ) : VpnServiceCallbacks {
//
//     private val featureRemoverReceiver: BroadcastReceiver = object : BroadcastReceiver() {
//         override fun onReceive(
//             context: Context,
//             intent: Intent
//         ) {
//             Timber.v("VpnFeatureRemoverReceiver onReceive $intent")
//             if (deviceShieldOnboardingStore.shouldRemoveVpnFeature()) {
//                 Timber.v("VpnFeatureRemoverReceiver removing Vpn Feature")
//                 deviceShieldOnboardingStore.removeVPNFeature()
//                 featureRemover.manuallyRemoveFeature()
//                 deviceShieldOnboardingStore.forgetRemoveVpnFeature()
//                 coroutineScope.launch {
//                     TrackerBlockingVpnService.stopService(context)
//                 }
//             } else {
//                 Timber.v("VpnFeatureRemoverReceiver should not remove Vpn Feature")
//             }
//         }
//     }
//
//     override fun onVpnStarted(coroutineScope: CoroutineScope) {
//         Timber.v("VpnFeatureRemoverReceiver onVpnStarted")
//         val intentFilter = IntentFilter().apply {
//             addAction(REMOVE_FEATURE)
//         }
//         context.registerReceiver(featureRemoverReceiver, intentFilter)
//         Timber.v("Receiver VpnFeatureRemoverReceiver registered")
//     }
//
//     override fun onVpnStopped(
//         coroutineScope: CoroutineScope,
//         vpnStopReason: VpnStopReason
//     ) {
//         kotlin.runCatching { context.unregisterReceiver(featureRemoverReceiver) }
//     }
//
//     companion object {
//
//         const val REMOVE_FEATURE = "com.duckduckgo.mobile.android.vpn.feature-removal"
//
//         fun removeFeatureIntent(): Intent {
//             return Intent(REMOVE_FEATURE)
//         }
//
//         fun isRemoveFeatureIntent(intent: Intent): Boolean {
//             return intent.action == REMOVE_FEATURE
//         }
//     }
// }
