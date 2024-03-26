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
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager
import com.duckduckgo.networkprotection.impl.subscription.isExpired
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class NetpVpnAccessRevokedDialogMonitor @Inject constructor(
    private val netpSubscriptionManager: NetpSubscriptionManager,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val betaEndedDialog: BetaEndedDialog,
    private val accessRevokedDialog: AccessRevokedDialog,
    private val subscriptions: Subscriptions,
    private val netPWaitlistRepository: NetPWaitlistRepository,
    private val networkProtectionState: NetworkProtectionState,
) : ActivityLifecycleCallbacks {

    private val conflatedJob = ConflatedJob()

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        conflatedJob += coroutineScope.launch(dispatcherProvider.io()) {
            delay(500) // debounce fast screen state changes, eg. resume -> pause -> resume
            if (shouldShowDialog()) {
                logcat { "VPN beta ended" }
                // Resetting here so we don't show this dialog anymore
                withContext(dispatcherProvider.main()) {
                    betaEndedDialog.show(activity)
                }
            } else if (netpSubscriptionManager.getVpnStatus().isExpired() && networkProtectionState.isOnboarded()) {
                // we don't want to show this dialog in eg. fresh installs
                withContext(dispatcherProvider.main()) {
                    accessRevokedDialog.showOnce(activity)
                }
            } else {
                logcat { "VPN access revoke dialog clear shown state" }
                accessRevokedDialog.clearIsShown()
            }
        }
    }

    override fun onActivityPaused(activity: Activity) {
        super.onActivityPaused(activity)
        conflatedJob.cancel()
    }

    private suspend fun shouldShowDialog(): Boolean {
        // Show dialog only if the pro is launched, user participated in beta (authentication was set - which only happens in beta)
        // AND dialog hasn't been shown before to the user.
        return betaEndedDialog.shouldShowDialog() && subscriptions.isEnabled() && netPWaitlistRepository.getAuthenticationToken() != null
    }
}
