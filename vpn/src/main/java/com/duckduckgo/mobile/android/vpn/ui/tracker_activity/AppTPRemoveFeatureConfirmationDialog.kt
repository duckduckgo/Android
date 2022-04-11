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
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.AppTPVpnConflictDialog.Companion
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AppTPRemoveFeatureConfirmationDialog private constructor(private val listener: Listener) : DialogFragment() {

    interface Listener {
        fun onCancel()
        fun onRemoveFeature()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val rootView = layoutInflater.inflate(R.layout.dialog_tracking_protection_remove_feature_confirm_disable, null)

        val cancelCta = rootView.findViewById<Button>(R.id.vpnFeatureRemoveDialogCancel)
        val removeCta = rootView.findViewById<Button>(R.id.vpnFeatureRemoveDialogRemove)

        val alertDialog = MaterialAlertDialogBuilder(
            requireActivity(),
            com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_RoundedDialog
        )
            .setView(rootView)

        isCancelable = false

        configureListeners(cancelCta, removeCta)

        return alertDialog.create()
    }

    private fun configureListeners(
        cancelCta: Button,
        removeCta: Button
    ) {
        cancelCta.setOnClickListener {
            dismiss()
            listener.onCancel()
        }
        removeCta.setOnClickListener {
            dismiss()
            listener.onRemoveFeature()
        }
    }
    companion object {

        const val TAG_APPTP_REMOVE_FEATURE_DIALOG = "AppTPRemoveFeatureDialog"

        fun instance(listener: Listener): AppTPRemoveFeatureConfirmationDialog {
            return AppTPRemoveFeatureConfirmationDialog(listener)
        }
    }
}
