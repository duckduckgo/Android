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
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder.EventListener
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import com.duckduckgo.networkprotection.impl.R.string
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface BetaEndedDialog {
    fun show(activity: Activity)
    fun shouldShowDialog(): Boolean
}

@ContributesBinding(AppScope::class)
class RealBetaEndedDialog @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val networkProtectionRepository: NetworkProtectionRepository,
    private val networkProtectionPixels: NetworkProtectionPixels,
    private val vpnSharedPreferencesProvider: VpnSharedPreferencesProvider,
) : BetaEndedDialog {

    private val preferences: SharedPreferences by lazy {
        vpnSharedPreferencesProvider.getSharedPreferences(
            FILENAME,
            multiprocess = true,
            migrate = false,
        )
    }
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

        networkProtectionPixels.reportPrivacyProPromotionDialogShown()
    }

    override fun shouldShowDialog(): Boolean = !hasShownBetaEndDialog()

    private fun resetVpnAccessRevokedState() {
        coroutineScope.launch(dispatcherProvider.io()) {
            networkProtectionRepository.vpnAccessRevoked = false
            storeBetaEndDialogShown()
        }
    }
    private fun storeBetaEndDialogShown() {
        preferences.edit(commit = true) {
            putBoolean(KEY_END_DIALOG_SHOWN, true)
        }
    }

    private fun hasShownBetaEndDialog(): Boolean = preferences.getBoolean(KEY_END_DIALOG_SHOWN, false)

    companion object {
        const val FILENAME = "com.duckduckgo.networkprotection.impl.waitlist.end.store.v1"
        const val KEY_END_DIALOG_SHOWN = "KEY_END_DIALOG_SHOWN"
    }
}
