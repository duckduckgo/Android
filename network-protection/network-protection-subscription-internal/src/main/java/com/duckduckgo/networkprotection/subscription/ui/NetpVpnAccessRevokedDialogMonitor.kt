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

package com.duckduckgo.networkprotection.subscription.ui

import android.app.Activity
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browser.api.ActivityLifecycleCallbacks
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder.EventListener
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.subscription.R.string
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionScreenNoParams
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class NetpVpnAccessRevokedDialogMonitor @Inject constructor(
    private val networkProtectionRepository: NetworkProtectionRepository,
    private val globalActivityStarter: GlobalActivityStarter,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : ActivityLifecycleCallbacks {
    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        coroutineScope.launch(dispatcherProvider.io()) {
            if (networkProtectionRepository.vpnAccessRevoked) {
                withContext(dispatcherProvider.main()) {
                    TextAlertDialogBuilder(activity)
                        .setTitle(string.netpDialogVpnAccessRevokedTitle)
                        .setMessage(string.netpDialogVpnAccessRevokedBody)
                        .setPositiveButton(string.netpDialogVpnAccessRevokedPositiveAction)
                        .setNegativeButton(string.netpDialogVpnAccessRevokedNegativeAction)
                        .addEventListener(
                            object : EventListener() {
                                override fun onPositiveButtonClicked() {
                                    globalActivityStarter.start(activity, SubscriptionScreenNoParams)
                                    resetVpnAccessRevokedState()
                                }

                                override fun onNegativeButtonClicked() {
                                    resetVpnAccessRevokedState()
                                }
                            },
                        )
                        .show()
                }
            }
        }
    }

    private fun resetVpnAccessRevokedState() {
        coroutineScope.launch(dispatcherProvider.io()) {
            networkProtectionRepository.vpnAccessRevoked = false
        }
    }
}
