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
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder.EventListener
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.R.string
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface BetaEndedDialog {
    fun show(activity: Activity)
}

@ContributesBinding(AppScope::class)
class RealBetaEndedDialog @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val networkProtectionRepository: NetworkProtectionRepository,
    private val betaEndStore: BetaEndStore,
) : BetaEndedDialog {

    private var boundActivity: Activity? = null

    override fun show(activity: Activity) {
        if (boundActivity == activity) return

        boundActivity = activity

        TextAlertDialogBuilder(activity)
            .setTitle(string.netpWaitlistEndDialogTitle)
            .setMessage(string.netpWaitlistEndDialogSubtitle)
            .setPositiveButton(string.netpWaitlistEndDialogAction)
            .addEventListener(
                object : EventListener() {
                    override fun onPositiveButtonClicked() {
                        // Commenting this for now since this is still behind the subs build
                        // globalActivityStarter.start(activity, SubscriptionScreenNoParams)
                        resetVpnAccessRevokedState()
                    }
                },
            )
            .show()
    }

    private fun resetVpnAccessRevokedState() {
        coroutineScope.launch(dispatcherProvider.io()) {
            networkProtectionRepository.vpnAccessRevoked = false
            betaEndStore.showBetaEndDialog()
        }
    }
}
