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

package com.duckduckgo.autofill.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.autofill.CredentialAutofillPickerDialog
import com.duckduckgo.autofill.CredentialAutofillPickerDialog.Companion.RESULT_KEY_CREDENTIAL_PICKER
import com.duckduckgo.autofill.CredentialAutofillPickerDialogFactory
import com.duckduckgo.autofill.Credentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.ui.credential.CredentialsAdapter
import com.duckduckgo.mobile.android.ui.view.toPx
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CredentialAutofillPickerDialogFragment : BottomSheetDialogFragment(), CredentialAutofillPickerDialog {

    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        dialog?.setOnShowListener {
            val d = it as BottomSheetDialog
            val sheet: FrameLayout = d.findViewById(com.google.android.material.R.id.design_bottom_sheet)!!

            d.findViewById<TextView>(R.id.shareCredentialsTitle)?.text = "login form detected"
            BottomSheetBehavior.from(sheet).setPeekHeight(600.toPx(), true)
        }

        return inflater.inflate(R.layout.content_autofill_credentials_tooltip, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.availableCredentialsRecycler)
        recyclerView.adapter =
            CredentialsAdapter(getAvailableCredentials()) { selectedCredentials ->
                val result =
                    Bundle().also {
                        it.putString("url", getOriginalUrl())
                        it.putParcelable("cred", selectedCredentials)
                    }
                parentFragment?.setFragmentResult(RESULT_KEY_CREDENTIAL_PICKER, result)
                dismiss()
            }
    }

    private fun getAvailableCredentials() = arguments?.getParcelableArrayList<Credentials>("creds")!!

    private fun getOriginalUrl() = arguments?.getString("url")!!

    // needed to avoid an untyped cast when wanting to show DialogFragment, as outside this module
    // it is known by its interface CredentialAutofillPickerDialog, not as a DialogFragment.
    override fun asDialogFragment(): DialogFragment = this

    companion object {

        fun instance(
            url: String,
            credentials: List<Credentials>
        ): CredentialAutofillPickerDialogFragment {

            val cr = ArrayList<Credentials>(credentials)

            val fragment = CredentialAutofillPickerDialogFragment()
            fragment.arguments =
                Bundle().also {
                    it.putString("url", url)
                    it.putParcelableArrayList("creds", cr)
                }
            return fragment
        }
    }
}

class CredentialAutofillPickerDialogAndroidFactory : CredentialAutofillPickerDialogFactory {

    override fun create(url: String, credentials: List<Credentials>): CredentialAutofillPickerDialog {
        return CredentialAutofillPickerDialogFragment.instance(url, credentials)
    }
}
