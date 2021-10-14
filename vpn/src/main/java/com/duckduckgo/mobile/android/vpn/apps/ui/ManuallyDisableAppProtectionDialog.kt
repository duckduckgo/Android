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
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppInfo
import com.duckduckgo.mobile.android.vpn.ui.notification.applyBoldSpanTo
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ManuallyDisableAppProtectionDialog : DialogFragment() {

    interface ManuallyDisableAppProtectionDialogListener {
        fun onAppProtectionDisabled(answer: Int, appName: String, packageName: String)
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

        val rootView = layoutInflater.inflate(R.layout.dialog_tracking_protection_manually_disable_app, null)

        val appIcon = rootView.findViewById<ImageView>(R.id.trackingProtectionAppIcon)
        val disableLabel = rootView.findViewById<TextView>(R.id.trackingProtectionAppLabel)
        val submitCTA = rootView.findViewById<Button>(R.id.trackingProtectionExcludeAppDialogSubmit)
        val radioGroup = rootView.findViewById<RadioGroup>(R.id.trackingProtectionAppRadioGroup)

        val alertDialog = MaterialAlertDialogBuilder(requireActivity(), com.duckduckgo.mobile.android.R.style.Widget_DuckDuckGo_RoundedDialog)
            .setView(rootView)

        validateBundleArguments()
        isCancelable = false

        populateAppIcon(appIcon)
        populateLabel(disableLabel)
        configureListeners(submitCTA, radioGroup)

        return alertDialog.create()
    }

    private fun populateAppIcon(appIcon: ImageView) {
        val icon = requireActivity().packageManager.safeGetApplicationIcon(getPackageName())
        appIcon.setImageDrawable(icon)
    }

    private fun populateLabel(disableLabel: TextView) {
        disableLabel.text =
            getString(R.string.atp_ExcludeAppsManuallyDisableAppLabel, getAppName()).applyBoldSpanTo(getAppName())
    }

    private fun getPackageName(): String {
        return requireArguments().getString(KEY_APP_PACKAGE_NAME)!!
    }

    private fun getDialogAnswer(radioGroup: RadioGroup): Int {
        return when (radioGroup.checkedRadioButtonId) {
            R.id.trackingProtectionAppRadioOne -> STOPPED_WORKING
            R.id.trackingProtectionAppRadioTwo -> TRACKING_OK
            else -> DONT_USE
        }
    }

    private fun getAppName(): String {
        return requireArguments().getString(KEY_APP_NAME)!!
    }

    private fun configureListeners(
        submitCTA: Button,
        radioGroup: RadioGroup
    ) {
        submitCTA.setOnClickListener {
            dismiss()
            listener.onAppProtectionDisabled(getDialogAnswer(radioGroup), appName = getAppName(), packageName = getPackageName())
        }

        radioGroup.setOnCheckedChangeListener { _, _ ->
            submitCTA.isEnabled = true
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
    }

    companion object {

        const val TAG_MANUALLY_EXCLUDE_APPS_DISABLE = "ManuallyExcludedAppsDialogDisable"
        private const val KEY_APP_PACKAGE_NAME = "KEY_APP_PACKAGE_NAME"
        private const val KEY_APP_NAME = "KEY_APP_NAME"

        const val NO_REASON_NEEDED = 0
        const val STOPPED_WORKING = 1
        const val TRACKING_OK = 2
        const val DONT_USE = 3

        fun instance(appInfo: TrackingProtectionAppInfo): ManuallyDisableAppProtectionDialog {
            return ManuallyDisableAppProtectionDialog().also { fragment ->
                val bundle = Bundle()
                bundle.putString(KEY_APP_PACKAGE_NAME, appInfo.packageName)
                bundle.putString(KEY_APP_NAME, appInfo.name)
                fragment.arguments = bundle
            }
        }
    }
}
