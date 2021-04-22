/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.settings

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.email.EmailManager
import com.google.android.material.textview.MaterialTextView
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

class SettingsEmailLogoutDialog : DialogFragment() {

    @Inject
    lateinit var emailManager: EmailManager

    var onLogout: (() -> Unit) = {}

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val rootView = View.inflate(activity, R.layout.settings_email_logout_fragment, null)
        val message = rootView.findViewById<MaterialTextView>(R.id.emailDialogText)
        message.text = getString(R.string.settingsEmailAutofillEnabledFor, emailManager.getEmailAddress().orEmpty())

        val alertBuilder = AlertDialog.Builder(requireActivity())
            .setView(rootView)
            .setTitle(getString(R.string.settingsEmailAutofill))
            .setNegativeButton(R.string.autofillSettingCancel) { _, _ ->
                dismiss()
            }
            .setPositiveButton(R.string.autofillSettingDisable) { _, _ ->
                dialog?.let {
                    onLogout()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }

        return alertBuilder.create()
    }

    companion object {
        fun create(): SettingsEmailLogoutDialog = SettingsEmailLogoutDialog()
    }

}
