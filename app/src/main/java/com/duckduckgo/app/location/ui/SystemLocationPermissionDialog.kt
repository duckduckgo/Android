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
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.faviconLocation
import com.duckduckgo.app.global.image.GlideApp
import com.duckduckgo.app.global.view.website
import org.jetbrains.anko.find

class SystemLocationPermissionDialog : DialogFragment() {

    interface SystemLocationPermissionDialogListener {
        fun onSystemLocationPermissionAllowed()
        fun onSystemLocationPermissionNotAllowed()
        fun onSystemLocationPermissionNeverAllowed()
    }

    val listener: SystemLocationPermissionDialogListener
        get() = parentFragment as SystemLocationPermissionDialogListener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false

        val rootView = layoutInflater.inflate(R.layout.content_system_location_permission_dialog, null)

        val subtitle = rootView.find<TextView>(R.id.systemPermissionDialogSubtitle)
        val favicon = rootView.find<ImageView>(R.id.faviconImage)
        val allowLocationPermission = rootView.find<TextView>(R.id.allowLocationPermission)
        val denyLocationPermission = rootView.find<TextView>(R.id.denyLocationPermission)
        val neverAllowLocationPermission = rootView.find<TextView>(R.id.neverAllowLocationPermission)

        val alertBuilder = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
            .setView(rootView)

        validateBundleArguments()
        populateSubtitle(subtitle)
        populateFavicon(favicon)
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

    private fun populateSubtitle(view: TextView) {
        arguments?.let { args ->
            val originUrl = args.getString(KEY_REQUEST_ORIGIN).website()
            val subtitle = getString(R.string.preciseLocationSystemDialogSubtitle, originUrl, originUrl)
            view.text = subtitle
        }
    }

    private fun populateFavicon(imageView: ImageView) {
        arguments?.let { args ->
            val originUrl = args.getString(KEY_REQUEST_ORIGIN)
            val faviconUrl = Uri.parse(originUrl).faviconLocation()

            GlideApp.with(requireContext())
                .load(faviconUrl)
                .error(R.drawable.ic_globe_gray_16dp)
                .into(imageView)
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
