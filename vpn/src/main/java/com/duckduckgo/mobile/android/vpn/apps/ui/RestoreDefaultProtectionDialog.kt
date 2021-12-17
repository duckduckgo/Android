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

package com.duckduckgo.mobile.android.vpn.apps.ui

import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import androidx.fragment.app.DialogFragment
import com.duckduckgo.mobile.android.vpn.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class RestoreDefaultProtectionDialog : DialogFragment() {

    interface RestoreDefaultProtectionDialogListener {
        fun onDefaultProtectionRestored()
    }

    val listener: RestoreDefaultProtectionDialogListener
        get() {
            return if (parentFragment is RestoreDefaultProtectionDialogListener) {
                parentFragment as RestoreDefaultProtectionDialogListener
            } else {
                activity as RestoreDefaultProtectionDialogListener
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val rootView =
            layoutInflater.inflate(R.layout.dialog_tracking_protection_restore_defaults, null)

        val restoreCTA =
            rootView.findViewById<Button>(R.id.trackingProtectionRestoreDefaultsRestore)
        val cancelCTA = rootView.findViewById<Button>(R.id.trackingProtectionRestoreDefaultsCancel)

        val alertDialog =
            MaterialAlertDialogBuilder(
                    requireActivity(),
                    com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_RoundedDialog)
                .setView(rootView)

        isCancelable = false

        configureListeners(restoreCTA, cancelCTA)

        return alertDialog.create()
    }

    private fun configureListeners(restoreCTA: Button, cancelCTA: Button) {
        restoreCTA.setOnClickListener {
            dismiss()
            listener.onDefaultProtectionRestored()
        }
        cancelCTA.setOnClickListener { dismiss() }
    }

    companion object {

        const val TAG_RESTORE_DEFAULT_PROTECTION = "RestoreDefaultProtection"

        fun instance(): RestoreDefaultProtectionDialog {
            return RestoreDefaultProtectionDialog()
        }
    }
}
