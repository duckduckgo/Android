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
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.networkprotection.impl.R
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionPurchase
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface AccessRevokedDialog {
    /**
     * Call this method to always show the dialog
     */
    fun showAlways(activity: Activity)

    /**
     * Call this method to show the dialog only once.
     * Use [clearIsShown] to reset that state
     */
    fun showOnce(activity: Activity)

    /**
     * Call this method to allow [showOnce] to show the dialog more than once
     */
    fun clearIsShown()
}

@ContributesBinding(AppScope::class)
class RealAccessRevokedDialog @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val globalActivityStarter: GlobalActivityStarter,
    private val networkProtectionPixels: NetworkProtectionPixels,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : AccessRevokedDialog {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(
            FILENAME,
            multiprocess = true,
            migrate = false,
        )
    }

    private var boundActivity: Activity? = null

    override fun showAlways(activity: Activity) {
        showInternal(activity)
    }

    override fun showOnce(activity: Activity) {
        coroutineScope.launch {
            if (!isShown()) {
                withContext(dispatcherProvider.main()) {
                    showInternal(activity)
                }
            }
        }
    }

    private fun showInternal(activity: Activity) {
        if (boundActivity == activity) return

        boundActivity = activity

        TextAlertDialogBuilder(activity)
            .setTitle(R.string.netpDialogVpnAccessRevokedTitle)
            .setMessage(R.string.netpDialogVpnAccessRevokedBody)
            .setPositiveButton(R.string.netpDialogVpnAccessRevokedPositiveAction)
            .setNegativeButton(R.string.netpDialogVpnAccessRevokedNegativeAction)
            .addEventListener(
                object : EventListener() {
                    override fun onPositiveButtonClicked() {
                        // Commenting this for now since this is still behind the subs build
                        globalActivityStarter.start(activity, SubscriptionPurchase())
                    }

                    override fun onNegativeButtonClicked() {}

                    override fun onDialogDismissed() {
                        coroutineScope.launch {
                            markAsShown()
                        }
                    }
                },
            )
            .show()

        networkProtectionPixels.reportAccessRevokedDialogShown()
    }

    override fun clearIsShown() {
        coroutineScope.launch(dispatcherProvider.io()) {
            preferences.edit(commit = true) {
                putBoolean(KEY_END_DIALOG_SHOWN, false)
            }
        }
    }

    private suspend fun isShown(): Boolean = withContext(dispatcherProvider.io()) {
        return@withContext preferences.getBoolean(KEY_END_DIALOG_SHOWN, false)
    }

    private suspend fun markAsShown() = withContext(dispatcherProvider.io()) {
        preferences.edit(commit = true) {
            putBoolean(KEY_END_DIALOG_SHOWN, true)
        }
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.networkprotection.dialog.access.revoked.store.v1"
        private const val KEY_END_DIALOG_SHOWN = "KEY_END_DIALOG_SHOWN"
    }
}
