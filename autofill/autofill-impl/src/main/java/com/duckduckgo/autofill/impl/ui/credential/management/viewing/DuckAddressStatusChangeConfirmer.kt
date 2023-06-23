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

package com.duckduckgo.autofill.impl.ui.credential.management.viewing

import android.content.Context
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.view.dialog.TextAlertDialogBuilder
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface DuckAddressStatusChangeConfirmer {

    fun showConfirmationToActivate(
        context: Context,
        duckAddress: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    )

    fun showConfirmationToDeactivate(
        context: Context,
        duckAddress: String,
        onConfirm: () -> Unit,
        onCancel: () -> Unit,
    )

    @ContributesBinding(FragmentScope::class)
    class ConfirmationDialogStatusChangeConfirmer @Inject constructor() : DuckAddressStatusChangeConfirmer {

        override fun showConfirmationToActivate(
            context: Context,
            duckAddress: String,
            onConfirm: () -> Unit,
            onCancel: () -> Unit,
        ) {
            TextAlertDialogBuilder(context)
                .setTitle(R.string.credentialManagementActivateDuckAddressDialogTitle)
                .setMessage(context.getString(R.string.credentialManagementActivateDuckAddressDialogMessage, duckAddress))
                .setPositiveButton(R.string.credentialManagementActivateDuckAddressPositiveButton)
                .setNegativeButton(R.string.autofillDeleteLoginDialogCancel)
                .setCancellable(true)
                .addEventListener(
                    object : TextAlertDialogBuilder.EventListener() {
                        override fun onPositiveButtonClicked() = onConfirm()
                        override fun onDialogCancelled() = onCancel()
                        override fun onNegativeButtonClicked() = onCancel()
                    },
                )
                .show()
        }

        override fun showConfirmationToDeactivate(
            context: Context,
            duckAddress: String,
            onConfirm: () -> Unit,
            onCancel: () -> Unit,
        ) {
            TextAlertDialogBuilder(context)
                .setTitle(R.string.credentialManagementDeactivateDuckAddressDialogTitle)
                .setMessage(context.getString(R.string.credentialManagementDeactivateDuckAddressDialogMessage, duckAddress))
                .setPositiveButton(R.string.credentialManagementDeactivateDuckAddressPositiveButton)
                .setNegativeButton(R.string.autofillDeleteLoginDialogCancel)
                .setCancellable(true)
                .addEventListener(
                    object : TextAlertDialogBuilder.EventListener() {
                        override fun onPositiveButtonClicked() = onConfirm()
                        override fun onDialogCancelled() = onCancel()
                        override fun onNegativeButtonClicked() = onCancel()
                    },
                )
                .show()
        }
    }
}
