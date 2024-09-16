/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.exclusion.ui

import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.duckduckgo.common.utils.extensions.safeGetApplicationIcon
import com.duckduckgo.networkprotection.impl.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ManuallyDisableAppProtectionDialog : DialogFragment() {

    interface ManuallyDisableAppProtectionDialogListener {
        fun onAppProtectionDisabled(
            appName: String,
            packageName: String,
            report: Boolean = false,
        )
    }

    val listener: ManuallyDisableAppProtectionDialogListener
        get() {
            return if (parentFragment is ManuallyDisableAppProtectionDialogListener) {
                parentFragment as ManuallyDisableAppProtectionDialogListener
            } else {
                activity as ManuallyDisableAppProtectionDialogListener
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val rootView = layoutInflater.inflate(R.layout.dialog_netp_manually_disable_app, null)

        val appIcon = rootView.findViewById<ImageView>(R.id.netp_disable_dialog_app_icon)
        val appLabel = rootView.findViewById<TextView>(R.id.netp_disable_dialog_label)
        val reportCTA = rootView.findViewById<Button>(R.id.netp_disable_dialog_action_report)
        val skipCTA = rootView.findViewById<Button>(R.id.netp_disable_dialog_action_skip)

        val alertDialog = MaterialAlertDialogBuilder(requireActivity(), com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_Dialog)
            .setView(rootView)

        validateBundleArguments()
        isCancelable = false

        populateAppIcon(appIcon)
        populateAppName(appLabel)
        configureListeners(reportCTA, skipCTA)

        return alertDialog.create()
    }

    private fun populateAppIcon(appIcon: ImageView) {
        val icon = requireActivity().packageManager.safeGetApplicationIcon(getPackageName())
        appIcon.setImageDrawable(icon)
    }

    private fun populateAppName(appLabel: TextView) {
        appLabel.text = getString(R.string.netpExclusionListManuallyDisableAppDialogLabel, getAppName())
    }

    private fun getPackageName(): String {
        return requireArguments().getString(KEY_APP_PACKAGE_NAME)!!
    }

    private fun getAppName(): String {
        return requireArguments().getString(KEY_APP_NAME)!!
    }

    private fun configureListeners(
        reportCTA: Button,
        skipCTA: Button,
    ) {
        reportCTA.setOnClickListener {
            dismiss()
            listener.onAppProtectionDisabled(getAppName(), getPackageName(), true)
        }

        skipCTA.setOnClickListener {
            dismiss()
            listener.onAppProtectionDisabled(getAppName(), getPackageName())
        }
    }

    private fun validateBundleArguments() {
        val args = requireArguments()
        if (!args.containsKey(KEY_APP_PACKAGE_NAME)) {
            throw IllegalArgumentException("Bundle arguments required [KEY_APP_PACKAGE_NAME")
        }
        if (args.getString(KEY_APP_NAME) == null) {
            throw IllegalArgumentException("Bundle arguments can't be null [KEY_APP_NAME")
        }
    }

    companion object {
        const val TAG_MANUALLY_EXCLUDE_APPS_DISABLE = "ManuallyExcludedAppsDialogDisable"
        private const val KEY_APP_PACKAGE_NAME = "KEY_APP_PACKAGE_NAME"
        private const val KEY_APP_NAME = "KEY_APP_NAME"

        fun instance(appInfo: NetpExclusionListApp): ManuallyDisableAppProtectionDialog {
            return ManuallyDisableAppProtectionDialog().also { fragment ->
                val bundle = Bundle()
                bundle.putString(KEY_APP_PACKAGE_NAME, appInfo.packageName)
                bundle.putString(KEY_APP_NAME, appInfo.name)
                fragment.arguments = bundle
            }
        }
    }
}
