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
import com.duckduckgo.app.location.data.LocationPermissionType
import org.jetbrains.anko.find

class SiteLocationPermissionDialog : DialogFragment() {

    interface Listener {
        fun onSiteLocationPermissionSelected(domain: String, permission: LocationPermissionType)
    }

    private lateinit var listener: Listener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false

        val rootView = layoutInflater.inflate(R.layout.content_site_location_permission_dialog, null)

        val title = rootView.find<TextView>(R.id.sitePermissionDialogTitle)
        val favicon = rootView.find<ImageView>(R.id.sitePermissionDialogFavicon)
        val allowAlways = rootView.find<TextView>(R.id.siteAllowAlwaysLocationPermission)
        val allowOnce = rootView.find<TextView>(R.id.siteAllowOnceLocationPermission)
        val denyOnce = rootView.find<TextView>(R.id.siteDenyOnceLocationPermission)
        val denyAlways = rootView.find<TextView>(R.id.siteDenyAlwaysLocationPermission)

        val alertDialog = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
            .setView(rootView)

        validateBundleArguments()
        populateTitle(title)
        populateFavicon(favicon)
        configureListeners(allowAlways, allowOnce, denyOnce, denyAlways)

        return alertDialog.create()
    }

    private fun populateTitle(title: TextView) {
        arguments?.let { args ->
            val originUrl = args.getString(KEY_REQUEST_ORIGIN)
            val dialogTitle = getString(R.string.preciseLocationSiteDialogTitle, originUrl.website())
            title.text = dialogTitle
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
        allowAlways: TextView,
        allowOnce: TextView,
        denyOnce: TextView,
        denyAlways: TextView
    ) {
        arguments?.let { args ->
            val originUrl = args.getString(KEY_REQUEST_ORIGIN)
            allowAlways.setOnClickListener {
                dismiss()
                listener.onSiteLocationPermissionSelected(originUrl, LocationPermissionType.ALLOW_ALWAYS)
            }
            allowOnce.setOnClickListener {
                dismiss()
                listener.onSiteLocationPermissionSelected(originUrl, LocationPermissionType.ALLOW_ONCE)
            }
            denyOnce.setOnClickListener {
                dismiss()
                listener.onSiteLocationPermissionSelected(originUrl, LocationPermissionType.DENY_ONCE)
            }
            denyAlways.setOnClickListener {
                dismiss()
                listener.onSiteLocationPermissionSelected(originUrl, LocationPermissionType.DENY_ALWAYS)
            }
        }
    }

    private fun validateBundleArguments() {
        if (arguments == null) throw IllegalArgumentException("Missing arguments bundle")
        val args = requireArguments()
        if (!args.containsKey(KEY_REQUEST_ORIGIN)) {
            throw IllegalArgumentException("Bundle arguments required [KEY_REQUEST_ORIGIN")
        }
    }

    companion object {

        const val SITE_LOCATION_PERMISSION_TAG = "SitemLocationPermission"
        private const val KEY_REQUEST_ORIGIN = "KEY_REQUEST_ORIGIN"

        fun instance(origin: String, listener: Listener): SiteLocationPermissionDialog {
            return SiteLocationPermissionDialog().also { fragment ->
                val bundle = Bundle()
                bundle.putString(KEY_REQUEST_ORIGIN, origin)
                fragment.arguments = bundle
                fragment.listener = listener
            }
        }
    }
}
