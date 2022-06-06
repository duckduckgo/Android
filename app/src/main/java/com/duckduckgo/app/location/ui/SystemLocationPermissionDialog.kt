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

package com.duckduckgo.app.location.ui

import android.app.Dialog
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentSystemLocationPermissionDialogBinding
import com.duckduckgo.app.global.view.websiteFromGeoLocationsApiOrigin
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class SystemLocationPermissionDialog : DialogFragment() {

    private val binding by viewBinding(ContentSystemLocationPermissionDialogBinding::inflate)

    interface SystemLocationPermissionDialogListener {
        fun onSystemLocationPermissionAllowed()
        fun onSystemLocationPermissionNotAllowed()
        fun onSystemLocationPermissionNeverAllowed()
    }

    val listener: SystemLocationPermissionDialogListener
        get() = parentFragment as SystemLocationPermissionDialogListener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false

        val alertBuilder = AlertDialog.Builder(requireActivity()).setView(binding.root)

        validateBundleArguments()
        populateSubtitle(binding.systemPermissionDialogSubtitle)
        configureListeners(
            binding.allowLocationPermission,
            binding.denyLocationPermission,
            binding.neverAllowLocationPermission
        )

        return alertBuilder.create()
    }

    private fun validateBundleArguments() {
        if (arguments == null) throw IllegalArgumentException("Missing arguments bundle")
        val args = requireArguments()
        if (!args.containsKey(KEY_REQUEST_ORIGIN)) {
            throw IllegalArgumentException("Bundle arguments required [KEY_REQUEST_ORIGIN")
        }
    }

    private fun populateSubtitle(view: TextView) {
        arguments?.let { args ->
            val originUrl = args.getString(KEY_REQUEST_ORIGIN)!!.websiteFromGeoLocationsApiOrigin()
            val subtitle = getString(R.string.preciseLocationSystemDialogSubtitle, originUrl, originUrl)
            view.text = subtitle
        }
    }

    private fun configureListeners(
        allowLocationPermission: TextView,
        denyLocationPermission: TextView,
        neverAllowLocationPermission: TextView
    ) {
        allowLocationPermission.setOnClickListener {
            dismiss()
            listener.onSystemLocationPermissionAllowed()
        }
        denyLocationPermission.setOnClickListener {
            dismiss()
            listener.onSystemLocationPermissionNotAllowed()
        }
        neverAllowLocationPermission.setOnClickListener {
            dismiss()
            listener.onSystemLocationPermissionNeverAllowed()
        }
    }

    companion object {

        const val SYSTEM_LOCATION_PERMISSION_TAG = "SystemLocationPermission"
        private const val KEY_REQUEST_ORIGIN = "KEY_REQUEST_ORIGIN"

        fun instance(origin: String): SystemLocationPermissionDialog {
            return SystemLocationPermissionDialog().also { fragment ->
                val bundle = Bundle()
                bundle.putString(KEY_REQUEST_ORIGIN, origin)
                fragment.arguments = bundle
            }
        }
    }
}
