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
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.databinding.DialogTrackingProtectionRemoveFeatureConfirmDisableBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class VpnRemoveFeatureConfirmationDialog private constructor(private val listener: Listener) : DialogFragment(R.layout.dialog_tracking_protection_remove_feature_confirm_disable) {

    private val binding by viewBinding(DialogTrackingProtectionRemoveFeatureConfirmDisableBinding::inflate)

    interface Listener {
        fun OnRemoveFeatureDialogCancel()
        fun onRemoveFeature()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val alertDialog = MaterialAlertDialogBuilder(
            requireActivity(),
            com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_RoundedDialog
        )
            .setView(binding.root)

        isCancelable = false

        configureListeners(binding.vpnFeatureRemoveDialogCancel, binding.vpnFeatureRemoveDialogRemove)

        return alertDialog.create()
    }

    private fun configureListeners(
        cancelCta: Button,
        removeCta: Button
    ) {
        cancelCta.setOnClickListener {
            dismiss()
            listener.OnRemoveFeatureDialogCancel()
        }
        removeCta.setOnClickListener {
            dismiss()
            listener.onRemoveFeature()
        }
    }

    companion object {

        const val TAG_VPN_REMOVE_FEATURE_DIALOG = "VpnRemoveFeatureDialog"

        fun instance(listener: Listener): VpnRemoveFeatureConfirmationDialog {
            return VpnRemoveFeatureConfirmationDialog(listener)
        }
    }
}
