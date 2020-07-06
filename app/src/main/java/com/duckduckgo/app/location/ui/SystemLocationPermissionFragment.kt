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
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R
import org.jetbrains.anko.find
import java.util.Locale

class SystemLocationPermissionFragment : DialogFragment() {

    interface SystemLocationPermissionDialogListener {
        fun onSystemLocationPermissionAllowed()
        fun onSystemLocationPermissionNotAllowed()
        fun onSystemLocationPermissionNeverAllowed()
    }

    private lateinit var listener: SystemLocationPermissionDialogListener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false

        val rootView = layoutInflater.inflate(R.layout.content_system_location_permission_dialog, null)

        val title = rootView.find<TextView>(R.id.systemPermissionDialogTitle)
        val allowLocationPermission = rootView.find<TextView>(R.id.allowLocationPermission)
        val denyLocationPermission = rootView.find<TextView>(R.id.denyLocationPermission)
        val neverAllowLocationPermission = rootView.find<TextView>(R.id.neverAllowLocationPermission)

        val alertBuilder = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
            .setView(rootView)

        validateBundleArguments()
        populateTitle(title)
        configureListeners(allowLocationPermission, denyLocationPermission, neverAllowLocationPermission)

        return alertBuilder.create()
    }

    private fun validateBundleArguments() {
        if (arguments == null) throw IllegalArgumentException("Missing arguments bundle")
        val args = requireArguments()
        if (!args.containsKey(KEY_REQUEST_ORIGIN)) {
            throw IllegalArgumentException("Bundle arguments required [KEY_REQUEST_ORIGIN")
        }
    }

    private fun populateTitle(title: TextView) {
        arguments?.let { args ->
            val originUrl = args.getString(KEY_REQUEST_ORIGIN)
            val dialogTitle = String.format(Locale.US, getString(R.string.preciseLocationSystemDialogTitle), originUrl)
            title.text = dialogTitle
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

        fun instance(origin: String, listener: SystemLocationPermissionDialogListener): SystemLocationPermissionFragment {
            return SystemLocationPermissionFragment().also { fragment ->
                val bundle = Bundle()
                bundle.putString(KEY_REQUEST_ORIGIN, origin)
                fragment.arguments = bundle
                fragment.listener = listener
            }
        }
    }
}
