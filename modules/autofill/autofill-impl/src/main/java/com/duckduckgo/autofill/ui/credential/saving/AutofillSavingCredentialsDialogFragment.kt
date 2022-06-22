/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.ui.credential.saving

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.duckduckgo.autofill.CredentialSavePickerDialog
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.mobile.android.ui.view.toPx
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AutofillSavingCredentialsDialogFragment : BottomSheetDialogFragment(), CredentialSavePickerDialog {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dialog?.setOnShowListener {
            val dialog = it as BottomSheetDialog
            val sheet: FrameLayout = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet)!!
            BottomSheetBehavior.from(sheet).setPeekHeight(600.toPx(), true)
        }

        return inflater.inflate(R.layout.content_autofill_save_credentials_tooltip, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.saveLoginButton).setOnClickListener {

            val result = Bundle().also {
                it.putString(CredentialSavePickerDialog.KEY_URL, getOriginalUrl())
                it.putParcelable(CredentialSavePickerDialog.KEY_CREDENTIALS, getCredentialsToSave())
            }
            parentFragment?.setFragmentResult(CredentialSavePickerDialog.RESULT_KEY_CREDENTIAL_RESULT_SAVE, result)
            dismiss()
        }

        view.findViewById<Button>(R.id.cancelButton).setOnClickListener {
            dismiss()
        }

    }

    private fun getCredentialsToSave() = arguments?.getParcelable<LoginCredentials>(CredentialSavePickerDialog.KEY_CREDENTIALS)!!

    private fun getOriginalUrl() = arguments?.getString(CredentialSavePickerDialog.KEY_URL)!!

    // needed to avoid an untyped cast when wanting to show DialogFragment, as outside this module
    // it is known by its interface CredentialAutofillPickerDialog, not as a DialogFragment.
    override fun asDialogFragment(): DialogFragment = this

    companion object {

        fun instance(
            url: String,
            credentials: LoginCredentials
        ): AutofillSavingCredentialsDialogFragment {

            val fragment = AutofillSavingCredentialsDialogFragment()
            fragment.arguments =
                Bundle().also {
                    it.putString(CredentialSavePickerDialog.KEY_URL, url)
                    it.putParcelable(CredentialSavePickerDialog.KEY_CREDENTIALS, credentials)
                }
            return fragment
        }
    }
}
