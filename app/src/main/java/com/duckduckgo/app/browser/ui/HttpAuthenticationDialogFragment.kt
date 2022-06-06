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

package com.duckduckgo.app.browser.ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.HttpAuthenticationBinding
import com.duckduckgo.app.browser.model.BasicAuthenticationCredentials
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.mobile.android.ui.view.hideKeyboard
import com.duckduckgo.mobile.android.ui.view.showKeyboard
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class HttpAuthenticationDialogFragment : DialogFragment() {

    private var didUserCompleteAuthentication: Boolean = false
    lateinit var request: BasicAuthenticationRequest
    var listener: HttpAuthenticationListener? = null

    private val binding by viewBinding(HttpAuthenticationBinding::inflate)
    interface HttpAuthenticationListener {
        fun handleAuthentication(
            request: BasicAuthenticationRequest,
            credentials: BasicAuthenticationCredentials
        )

        fun cancelAuthentication(request: BasicAuthenticationRequest)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        validateBundleArguments()
        val url = requireArguments().getString(KEY_TARGET_URL)
        binding.httpAuthInformationText.text = getString(R.string.authenticationDialogMessage, url)

        val alertBuilder = AlertDialog.Builder(requireActivity())
            .setView(binding.root)
            .setPositiveButton(R.string.authenticationDialogPositiveButton) { _, _ ->
                listener?.handleAuthentication(
                    request,
                    BasicAuthenticationCredentials(username = binding.usernameInput.text.toString(), password = binding.passwordInput.text.toString())
                )
                didUserCompleteAuthentication = true
            }.setNegativeButton(R.string.authenticationDialogNegativeButton) { _, _ ->
                binding.root.hideKeyboard()
                listener?.cancelAuthentication(request)
                didUserCompleteAuthentication = true
            }
            .setTitle(R.string.authenticationDialogTitle)

        val alert = alertBuilder.create()
        showKeyboard(binding.usernameInput, alert)
        return alert
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!didUserCompleteAuthentication) {
            listener?.cancelAuthentication(request)
        }
    }

    private fun validateBundleArguments() {
        if (arguments == null) throw IllegalArgumentException("Missing arguments bundle")
        val args = requireArguments()
        if (!args.containsKey(KEY_TARGET_URL)) {
            throw IllegalArgumentException("Bundle arguments required [KEY_TARGET_URL]")
        }
    }

    private fun showKeyboard(
        editText: EditText,
        alert: AlertDialog
    ) {
        editText.showKeyboard()
        alert.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
    }

    companion object {
        private const val KEY_TARGET_URL = "KEY_TARGET_URL"

        fun createHttpAuthenticationDialog(url: String): HttpAuthenticationDialogFragment {
            val dialog = HttpAuthenticationDialogFragment()
            val bundle = Bundle()
            bundle.putString(KEY_TARGET_URL, url)
            dialog.arguments = bundle
            return dialog
        }
    }
}
