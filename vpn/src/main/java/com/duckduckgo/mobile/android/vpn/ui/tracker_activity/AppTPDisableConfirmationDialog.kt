/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity

import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import androidx.fragment.app.DialogFragment
import com.duckduckgo.mobile.android.vpn.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AppTPDisableConfirmationDialog : DialogFragment() {

    interface AppTPDisableConfirmationDialogListener {
        fun onOpenAppProtection()
        fun onTurnAppTrackingProtectionOff()
        fun onDisableDialogCancelled()
    }

    val listener: AppTPDisableConfirmationDialogListener
        get() {
            return if (parentFragment is AppTPDisableConfirmationDialogListener) {
                parentFragment as AppTPDisableConfirmationDialogListener
            } else {
                activity as AppTPDisableConfirmationDialogListener
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val rootView =
            layoutInflater.inflate(R.layout.dialog_tracking_protection_confirm_disable, null)

        val disableOneApp = rootView.findViewById<Button>(R.id.disableConfirmationDialogOneApp)
        val disableAllApps = rootView.findViewById<Button>(R.id.disableConfirmationDialogAllApps)
        val cancel = rootView.findViewById<Button>(R.id.disableConfirmationDialogCancel)

        val alertDialog =
            MaterialAlertDialogBuilder(
                    requireActivity(),
                    com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_RoundedDialog)
                .setView(rootView)

        isCancelable = false

        configureListeners(disableOneApp, disableAllApps, cancel)

        return alertDialog.create()
    }

    private fun configureListeners(disableOneApp: Button, disableAllApps: Button, cancel: Button) {
        disableOneApp.setOnClickListener {
            dismiss()
            listener.onOpenAppProtection()
        }
        disableAllApps.setOnClickListener {
            dismiss()
            listener.onTurnAppTrackingProtectionOff()
        }
        cancel.setOnClickListener {
            dismiss()
            listener.onDisableDialogCancelled()
        }
    }

    companion object {

        const val TAG_APPTP_DISABLE_DIALOG = "AppTPDisableConfirmationDialog"

        fun instance(): AppTPDisableConfirmationDialog {
            return AppTPDisableConfirmationDialog()
        }
    }
}
