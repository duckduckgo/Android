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

package com.duckduckgo.mobile.android.vpn.apps.ui

import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppInfo
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppInfo.Companion.LOADS_WEBSITES_EXCLUSION_REASON
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ManuallyEnableAppProtectionDialog : DialogFragment() {

    interface ManuallyEnableAppsProtectionDialogListener {
        fun onAppProtectionEnabled(packageName: String, excludingReason: Int)
        fun onDialogSkipped(position: Int)
    }

    val listener: ManuallyEnableAppsProtectionDialogListener
        get() {
            return if (parentFragment is ManuallyEnableAppsProtectionDialogListener) {
                parentFragment as ManuallyEnableAppsProtectionDialogListener
            } else {
                activity as ManuallyEnableAppsProtectionDialogListener
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val rootView =
            layoutInflater.inflate(R.layout.dialog_tracking_protection_manually_enable_app, null)

        val appIcon = rootView.findViewById<ImageView>(R.id.trackingProtectionAppIcon)
        val appName = rootView.findViewById<TextView>(R.id.trackingProtectionAppName)
        val label = rootView.findViewById<TextView>(R.id.trackingProtectionAppLabel)
        val enableCTA = rootView.findViewById<Button>(R.id.trackingProtectionExlucdeAppDialogEnable)
        val skipCTA = rootView.findViewById<Button>(R.id.trackingProtectionExlucdeAppDialogSkip)

        val alertDialog =
            MaterialAlertDialogBuilder(
                    requireActivity(),
                    com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_RoundedDialog)
                .setView(rootView)

        validateBundleArguments()
        isCancelable = false

        populateAppIcon(appIcon)
        populateAppName(appName)
        populateText(label)
        configureListeners(enableCTA, skipCTA)

        return alertDialog.create()
    }

    private fun populateAppIcon(appIcon: ImageView) {
        val icon = requireActivity().packageManager.safeGetApplicationIcon(getPackageName())
        appIcon.setImageDrawable(icon)
    }

    private fun populateAppName(appName: TextView) {
        appName.text = getString(R.string.atp_ExcludeAppsManuallyEnableAppName, getAppName())
    }

    private fun populateText(label: TextView) {
        if (getExcludingReason() == LOADS_WEBSITES_EXCLUSION_REASON) {
            label.text = getString(R.string.atp_ExcludeAppsManuallyEnableLoadWebsitesAppLabel)
        } else {
            label.text = getString(R.string.atp_ExcludeAppsManuallyEnableAppLabel)
        }
    }

    private fun getPackageName(): String {
        return requireArguments().getString(KEY_APP_PACKAGE_NAME)!!
    }

    private fun getAppName(): String {
        return requireArguments().getString(KEY_APP_NAME)!!
    }

    private fun getExcludingReason(): Int {
        return requireArguments().getInt(KEY_EXCLUDING_REASON)!!
    }

    private fun getPosition(): Int {
        return requireArguments().getInt(KEY_POSITION)!!
    }

    private fun configureListeners(enableCTA: Button, skipCTA: Button) {
        enableCTA.setOnClickListener {
            dismiss()
            listener.onAppProtectionEnabled(getPackageName(), getExcludingReason())
        }
        skipCTA.setOnClickListener {
            dismiss()
            listener.onDialogSkipped(getPosition())
        }
    }

    private fun validateBundleArguments() {
        if (arguments == null) throw IllegalArgumentException("Missing arguments bundle")
        val args = requireArguments()
        if (!args.containsKey(KEY_APP_PACKAGE_NAME)) {
            throw IllegalArgumentException("Bundle arguments required [KEY_APP_PACKAGE_NAME")
        }
        if (args.getString(KEY_APP_NAME) == null) {
            throw IllegalArgumentException("Bundle arguments can't be null [KEY_APP_NAME")
        }
        if (!args.containsKey(KEY_EXCLUDING_REASON)) {
            throw IllegalArgumentException("Bundle arguments can't be null [KEY_EXCLUDING_REASON")
        }
        if (!args.containsKey(KEY_POSITION) == null) {
            throw IllegalArgumentException("Bundle arguments can't be null [KEY_POSITION")
        }
    }

    companion object {

        const val TAG_MANUALLY_EXCLUDE_APPS_ENABLE = "ManuallyExcludedAppsDialogEnable"
        private const val KEY_APP_PACKAGE_NAME = "KEY_APP_PACKAGE_NAME"
        private const val KEY_APP_NAME = "KEY_APP_NAME"
        private const val KEY_EXCLUDING_REASON = "KEY_EXCLUDING_REASON"
        private const val KEY_POSITION = "KEY_POSITION"

        fun instance(
            appInfo: TrackingProtectionAppInfo,
            position: Int
        ): ManuallyEnableAppProtectionDialog {
            return ManuallyEnableAppProtectionDialog().also { fragment ->
                val bundle = Bundle()
                bundle.putString(KEY_APP_PACKAGE_NAME, appInfo.packageName)
                bundle.putString(KEY_APP_NAME, appInfo.name)
                bundle.putInt(KEY_EXCLUDING_REASON, appInfo.knownProblem)
                bundle.putInt(KEY_POSITION, position)
                fragment.arguments = bundle
            }
        }
    }
}
