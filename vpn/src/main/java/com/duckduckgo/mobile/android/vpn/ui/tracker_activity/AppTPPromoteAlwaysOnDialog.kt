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

class AppTPPromoteAlwaysOnDialog private constructor(private val listener: Listener) : DialogFragment() {

    interface Listener {
        fun onPromoteAlwaysOnGoToVPNSettings()
        fun onPromoteAlwaysOnRemindLater()
        fun onPromoteAlwaysOnForget()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val rootView = layoutInflater.inflate(R.layout.dialog_tracking_protection_promote_always_on, null)

        val goToSettings = rootView.findViewById<Button>(R.id.promoteAlwaysOnDialogSettings)
        val remindLater = rootView.findViewById<Button>(R.id.promoteAlwaysOnDialogLater)
        val forget = rootView.findViewById<Button>(R.id.promoteAlwaysOnDialogForget)

        val alertDialog = MaterialAlertDialogBuilder(
            requireActivity(),
            com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_RoundedDialog
        )
            .setView(rootView)

        isCancelable = false

        configureListeners(goToSettings, remindLater, forget)

        return alertDialog.create()
    }

    private fun configureListeners(
        goToSettings: Button,
        remindLater: Button,
        forget: Button
    ) {
        goToSettings.setOnClickListener {
            dismiss()
            listener.onPromoteAlwaysOnGoToVPNSettings()
        }
        remindLater.setOnClickListener {
            dismiss()
            listener.onPromoteAlwaysOnRemindLater()
        }
        forget.setOnClickListener {
            dismiss()
            listener.onPromoteAlwaysOnForget()
        }
    }

    companion object {

        const val TAG_APPTP_PROMOTE_ALWAYS_ON_DIALOG = "AppTPPromoteAlwaysOnDialog"

        fun instance(listener: Listener): AppTPPromoteAlwaysOnDialog {
            return AppTPPromoteAlwaysOnDialog(listener)
        }
    }
}
