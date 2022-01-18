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
import com.duckduckgo.app.browser.databinding.ContentSiteLocationPermissionDialogBinding
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.mobile.android.ui.view.gone
import com.duckduckgo.app.global.view.websiteFromGeoLocationsApiOrigin
import com.duckduckgo.app.location.data.LocationPermissionType
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

class SiteLocationPermissionDialog : DialogFragment() {

    @Inject
    lateinit var faviconManager: FaviconManager

    private var faviconJob: Job? = null

    interface SiteLocationPermissionDialogListener {
        fun onSiteLocationPermissionSelected(
            domain: String,
            permission: LocationPermissionType
        )
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

        val binding = ContentSiteLocationPermissionDialogBinding.inflate(layoutInflater, null, false)

        val alertDialog = AlertDialog.Builder(requireActivity()).setView(binding.root)

        validateBundleArguments()
        populateTitle(binding.sitePermissionDialogTitle)
        populateSubtitle(binding.sitePermissionDialogSubtitle)
        populateFavicon(binding.sitePermissionDialogFavicon)
        configureListeners(
            binding.siteAllowAlwaysLocationPermission,
            binding.siteAllowOnceLocationPermission,
            binding.siteDenyOnceLocationPermission,
            binding.siteDenyAlwaysLocationPermission
        )
        hideExtraViews(
            binding.siteAllowOnceLocationPermission,
            binding.siteDenyOnceLocationPermission,
            binding.siteAllowOnceLocationPermissionDivider,
            binding.siteDenyLocationPermissionDivider
        )
        makeCancellable()

        return alertDialog.create()
    }

    override fun onDetach() {
        faviconJob?.cancel()
        super.onDetach()
    }

    private fun getOriginUrl(): String {
        return requireArguments().getString(KEY_REQUEST_ORIGIN)!!
    }

    private fun getTabId(): String {
        return requireArguments().getString(KEY_TAB_ID)!!
    }

    private fun isEditingPermissions(): Boolean {
        return requireArguments().getBoolean(KEY_EDITING_PERMISSION)
    }

    private fun populateTitle(title: TextView) {
        title.text = getString(R.string.preciseLocationSiteDialogTitle, getOriginUrl().websiteFromGeoLocationsApiOrigin())
    }

    private fun populateSubtitle(subtitle: TextView) {
        if (getOriginUrl().websiteFromGeoLocationsApiOrigin() == DDG_DOMAIN) {
            subtitle.text = getString(R.string.preciseLocationDDGDialogSubtitle)
        } else {
            subtitle.text = getString(R.string.preciseLocationSiteDialogSubtitle)
        }
    }

    private fun populateFavicon(imageView: ImageView) {
        val originUrl = getOriginUrl()
        val tabId = getTabId()

        faviconJob?.cancel()
        faviconJob = this.lifecycleScope.launch {
            faviconManager.loadToViewFromLocalOrFallback(tabId, originUrl, imageView)
        }
    }

    private fun configureListeners(
        allowAlways: TextView,
        allowOnce: TextView,
        denyOnce: TextView,
        denyAlways: TextView
    ) {
        val originUrl = getOriginUrl()
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

    private fun hideExtraViews(
        allowOnce: TextView,
        denyOnce: TextView,
        dividerOne: View,
        dividerTwo: View
    ) {
        if (isEditingPermissions()) {
            dividerOne.gone()
            dividerTwo.gone()
            allowOnce.gone()
            denyOnce.gone()
        }
    }

    private fun makeCancellable() {
        isCancelable = isEditingPermissions()
    }

    private fun validateBundleArguments() {
        if (arguments == null) throw IllegalArgumentException("Missing arguments bundle")
        val args = requireArguments()
        if (!args.containsKey(KEY_REQUEST_ORIGIN)) {
            throw IllegalArgumentException("Bundle arguments required [KEY_REQUEST_ORIGIN")
        }
        if (args.getString(KEY_REQUEST_ORIGIN) == null) {
            throw IllegalArgumentException("Bundle arguments can't be null [KEY_REQUEST_ORIGIN")
        }
        if (!args.containsKey(KEY_TAB_ID)) {
            throw IllegalArgumentException("Bundle arguments required [KEY_TAB_ID")
        }
        if (args.getString(KEY_TAB_ID) == null) {
            throw IllegalArgumentException("Bundle arguments can't be null [KEY_TAB_ID")
        }
        if (!args.containsKey(KEY_EDITING_PERMISSION)) {
            throw IllegalArgumentException("Bundle arguments required [KEY_EDITING_PERMISSION")
        }
    }

    companion object {

        const val SITE_LOCATION_PERMISSION_TAG = "SiteLocationPermission"
        private const val KEY_REQUEST_ORIGIN = "KEY_REQUEST_ORIGIN"
        private const val KEY_EDITING_PERMISSION = "KEY_SCREEN_FROM"
        private const val KEY_TAB_ID = "TAB_ID"

        private const val DDG_DOMAIN = "duckduckgo.com"

        fun instance(
            origin: String,
            isEditingPermission: Boolean,
            tabId: String
        ): SiteLocationPermissionDialog {
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
