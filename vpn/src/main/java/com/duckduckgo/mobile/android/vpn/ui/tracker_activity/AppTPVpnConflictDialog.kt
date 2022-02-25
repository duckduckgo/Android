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
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.duckduckgo.mobile.android.vpn.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AppTPVpnConflictDialog private constructor(private val listener: Listener) : DialogFragment() {

    interface Listener {
        fun onDismissConflictDialog()
        fun onOpenSettings()
        fun onContinue()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val rootView = layoutInflater.inflate(R.layout.dialog_tracking_protection_vpn_conflict, null)

        val endCta = rootView.findViewById<Button>(R.id.vpnConflictDialogEndCta)
        val startCta = rootView.findViewById<Button>(R.id.vpnConflictDialogStartCta)
        val titleView = rootView.findViewById<TextView>(R.id.vpnConflictDialogTitle)
        val messageView = rootView.findViewById<TextView>(R.id.vpnConflictDialogMessage)

        val alertDialog = MaterialAlertDialogBuilder(
            requireActivity(),
            com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_RoundedDialog
        )
            .setView(rootView)

        isCancelable = false

        configureViews(titleView, messageView, startCta, endCta)

        return alertDialog.create()
    }

    private fun configureViews(
        titleView: TextView,
        messageView: TextView,
        startCta: Button,
        endCta: Button
    ) {
        arguments?.let { args ->
            val isAlwaysOn = args.getBoolean(KEY_ALWAYS_ON_CONFLICT)
            if (isAlwaysOn) {
                configureAlwaysOnDialogViews(titleView, messageView, startCta, endCta)
            } else {
                configureVpnConflictViews(titleView, messageView, startCta, endCta)
            }
        }
    }

    private fun configureVpnConflictViews(
        titleView: TextView,
        messageView: TextView,
        startCta: Button,
        endCta: Button
    ) {
        configureVpnConflictText(titleView, messageView)
        configureVpnConflictListeners(startCta, endCta)
    }

    private fun configureVpnConflictText(
        titleView: TextView,
        messageView: TextView
    ) {
        titleView.setText(getText(R.string.atp_VpnConflictDialogTitle))
        messageView.setText(getText(R.string.atp_VpnConflictDialogMessage))
    }

    private fun configureVpnConflictListeners(
        startCta: Button,
        endCta: Button
    ) {
        endCta.setText(R.string.atp_VpnConflictDialogGotIt)
        endCta.setOnClickListener {
            dismiss()
            listener.onContinue()
        }
        startCta.setOnClickListener {
            dismiss()
            listener.onDismissConflictDialog()
        }
    }

    private fun configureAlwaysOnDialogViews(
        titleView: TextView,
        messageView: TextView,
        startCta: Button,
        endCta: Button
    ) {
        configureAlwaysOnText(titleView, messageView)
        configureAlwaysOnListeners(startCta, endCta)
    }

    private fun configureAlwaysOnText(
        titleView: TextView,
        messageView: TextView
    ) {
        titleView.setText(getText(R.string.atp_VpnConflictAlwaysOnDialogTitle))
        messageView.setText(getText(R.string.atp_VpnConflictDialogAlwaysOnMessage))
    }

    private fun configureAlwaysOnListeners(
        startCta: Button,
        endCta: Button
    ) {
        endCta.setText(R.string.atp_VpnConflictDialogOpenSettings)
        endCta.setOnClickListener {
            dismiss()
            listener.onOpenSettings()
        }
        startCta.setOnClickListener {
            dismiss()
            listener.onDismissConflictDialog()
        }
    }

    companion object {

        const val TAG_VPN_CONFLICT_DIALOG = "AppTPVpnConflictDialog"
        private const val KEY_ALWAYS_ON_CONFLICT = "KEY_ALWAYS_ON_CONFLICT"

        fun instance(listener: Listener, alwaysOn: Boolean = false): AppTPVpnConflictDialog {
            return AppTPVpnConflictDialog(listener).also { fragment ->
                val bundle = Bundle()
                bundle.putBoolean(KEY_ALWAYS_ON_CONFLICT, alwaysOn)
                fragment.arguments = bundle
            }
        }
    }
}
