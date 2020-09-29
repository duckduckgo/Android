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
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.view.gone
import com.duckduckgo.app.global.view.websiteFromGeoLocationsApiOrigin
import com.duckduckgo.app.location.data.LocationPermissionType
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.anko.find
import javax.inject.Inject

class SiteLocationPermissionDialog : DialogFragment() {

    @Inject
    lateinit var faviconManager: FaviconManager

    var faviconJob: Job? = null

    interface SiteLocationPermissionDialogListener {
        fun onSiteLocationPermissionSelected(domain: String, permission: LocationPermissionType)
    }

    val listener: SiteLocationPermissionDialogListener
        get() {
            return if (parentFragment is SiteLocationPermissionDialogListener) {
                parentFragment as SiteLocationPermissionDialogListener
            } else {
                activity as SiteLocationPermissionDialogListener
            }
        }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val rootView = layoutInflater.inflate(R.layout.content_site_location_permission_dialog, null)

        val title = rootView.find<TextView>(R.id.sitePermissionDialogTitle)
        val favicon = rootView.find<ImageView>(R.id.sitePermissionDialogFavicon)
        val allowAlways = rootView.find<TextView>(R.id.siteAllowAlwaysLocationPermission)
        val allowOnce = rootView.find<TextView>(R.id.siteAllowOnceLocationPermission)
        val denyOnce = rootView.find<TextView>(R.id.siteDenyOnceLocationPermission)
        val denyAlways = rootView.find<TextView>(R.id.siteDenyAlwaysLocationPermission)
        val extraDivider = rootView.find<View>(R.id.siteAllowOnceLocationPermissionDivider)
        val anotherDivider = rootView.find<View>(R.id.siteDenyLocationPermissionDivider)

        val alertDialog = AlertDialog.Builder(requireActivity(), R.style.AlertDialogTheme)
            .setView(rootView)

        validateBundleArguments()
        populateTitle(title)
        populateFavicon(favicon)
        configureListeners(allowAlways, allowOnce, denyOnce, denyAlways)
        hideExtraViews(allowOnce, denyOnce, extraDivider, anotherDivider)
        makeCancellable()

        return alertDialog.create()
    }

    override fun onDetach() {
        faviconJob?.cancel()
        super.onDetach()
    }

    private fun populateTitle(title: TextView) {
        arguments?.let { args ->
            val originUrl = args.getString(KEY_REQUEST_ORIGIN)!!
            val dialogTitle = getString(R.string.preciseLocationSiteDialogTitle, originUrl.websiteFromGeoLocationsApiOrigin())
            title.text = dialogTitle
        }
    }

    private fun populateFavicon(imageView: ImageView) {
        arguments?.let { args ->
            val originUrl = args.getString(KEY_REQUEST_ORIGIN)
            val tabId = args.getString(KEY_TAB_ID, "")

            originUrl?.let { url ->
                faviconJob?.cancel()
                faviconJob = this.lifecycleScope.launch {
                    if (tabId.isNotBlank()) {
                        faviconManager.loadToViewFromTemp(tabId, url, imageView)
                    } else {
                        faviconManager.loadToViewFromPersisted(url, imageView)
                    }
                }
            }
        }
    }

    private fun configureListeners(
        allowAlways: TextView,
        allowOnce: TextView,
        denyOnce: TextView,
        denyAlways: TextView
    ) {
        arguments?.let { args ->
            val originUrl = args.getString(KEY_REQUEST_ORIGIN)!!
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

    private fun hideExtraViews(
        allowOnce: TextView,
        denyOnce: TextView,
        dividerOne: View,
        dividerTwo: View
    ) {
        arguments?.let { args ->

            val isEditing = args.getBoolean(KEY_EDITING_PERMISSION)
            if (isEditing) {
                dividerOne.gone()
                dividerTwo.gone()
                allowOnce.gone()
                denyOnce.gone()
            }
        }
    }

    private fun makeCancellable() {
        arguments?.let { args ->
            val isEditing = args.getBoolean(KEY_EDITING_PERMISSION)
            isCancelable = isEditing
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

        const val SITE_LOCATION_PERMISSION_TAG = "SiteLocationPermission"
        private const val KEY_REQUEST_ORIGIN = "KEY_REQUEST_ORIGIN"
        private const val KEY_EDITING_PERMISSION = "KEY_SCREEN_FROM"
        private const val KEY_TAB_ID = "TAB_ID"

        fun instance(origin: String, isEditingPermission: Boolean, tabId: String): SiteLocationPermissionDialog {
            return SiteLocationPermissionDialog().also { fragment ->
                val bundle = Bundle()
                bundle.putString(KEY_REQUEST_ORIGIN, origin)
                bundle.putString(KEY_TAB_ID, tabId)
                bundle.putBoolean(KEY_EDITING_PERMISSION, isEditingPermission)
                fragment.arguments = bundle
            }
        }
    }
}
