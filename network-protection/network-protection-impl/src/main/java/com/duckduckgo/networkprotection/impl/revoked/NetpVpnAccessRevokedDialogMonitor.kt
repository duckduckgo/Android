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

package com.duckduckgo.networkprotection.impl.revoked

import android.app.Activity
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browser.api.ActivityLifecycleCallbacks
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.duckduckgo.subscriptions.api.Subscriptions
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
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val betaEndStore: BetaEndStore,
    private val netPWaitlistRepository: NetPWaitlistRepository,
    private val betaEndedDialog: BetaEndedDialog,
    private val accessRevokedDialog: AccessRevokedDialog,
    private val subscriptions: Subscriptions,
) : ActivityLifecycleCallbacks {

    init {
        coroutineScope.launch(dispatcherProvider.io()) {
            // We need to copy this value since once beta is not active, all stores are cleared.
            copyDidJoinBeta()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        coroutineScope.launch(dispatcherProvider.io()) {
            if (shouldShowDialog()) {
                // Resetting here so we don't show this dialog anymore
                networkProtectionRepository.vpnAccessRevoked = false
                betaEndStore.showBetaEndDialog()
                withContext(dispatcherProvider.main()) {
                    betaEndedDialog.show(activity)
                }
            } else if (networkProtectionRepository.vpnAccessRevoked) {
                withContext(dispatcherProvider.main()) {
                    accessRevokedDialog.show(activity)
                }
            }
        }
    }

    private suspend fun copyDidJoinBeta() {
        betaEndStore.storeUserParticipatedInBeta(netPWaitlistRepository.getAuthenticationToken() != null)
    }

    private suspend fun shouldShowDialog(): Boolean {
        // Show dialog only if the pro is launched, user participated in beta AND dialog hasn't been shown before to the user.
        return !betaEndStore.betaEndDialogShown() && subscriptions.isEnabled() && betaEndStore.didParticipateInBeta()
    }
}
