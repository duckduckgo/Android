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

package com.duckduckgo.autofill.impl.ui.credential.selecting

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.fragment.app.setFragmentResult
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.AutofillWebMessageRequest
import com.duckduckgo.autofill.api.CredentialAutofillPickerDialog
import com.duckduckgo.autofill.api.CredentialAutofillPickerDialog.Companion.KEY_CREDENTIALS
import com.duckduckgo.autofill.api.CredentialAutofillPickerDialog.Companion.KEY_TAB_ID
import com.duckduckgo.autofill.api.CredentialAutofillPickerDialog.Companion.KEY_TRIGGER_TYPE
import com.duckduckgo.autofill.api.CredentialAutofillPickerDialog.Companion.KEY_URL_REQUEST
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType
import com.duckduckgo.autofill.api.domain.app.LoginTriggerType.AUTOPROMPT
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.autofill.impl.databinding.ContentAutofillSelectCredentialsTooltipBinding
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SELECT_LOGIN_AUTOPROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SELECT_LOGIN_AUTOPROMPT_SELECTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SELECT_LOGIN_AUTOPROMPT_SHOWN
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SELECT_LOGIN_PROMPT_DISMISSED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SELECT_LOGIN_PROMPT_SELECTED
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames.AUTOFILL_SELECT_LOGIN_PROMPT_SHOWN
import com.duckduckgo.autofill.impl.ui.credential.dialog.animateClosed
import com.duckduckgo.autofill.impl.ui.credential.selecting.AutofillSelectCredentialsDialogFragment.DialogEvent.Dismissed
import com.duckduckgo.autofill.impl.ui.credential.selecting.AutofillSelectCredentialsDialogFragment.DialogEvent.Selected
import com.duckduckgo.autofill.impl.ui.credential.selecting.AutofillSelectCredentialsDialogFragment.DialogEvent.Shown
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ListItem
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import timber.log.Timber

@InjectWith(FragmentScope::class)
class AutofillSelectCredentialsDialogFragment : BottomSheetDialogFragment(), CredentialAutofillPickerDialog {

    @Inject
    lateinit var pixel: Pixel

    /**
     * To capture all the ways the BottomSheet can be dismissed, we might end up with onCancel being called when we don't want it
     * This flag is set to true when taking an action which dismisses the dialog, but should not be treated as a cancellation.
     */
    private var ignoreCancellationEvents = false

    override fun getTheme(): Int = R.style.AutofillBottomSheetDialogTheme

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var autofillSelectCredentialsGrouper: AutofillSelectCredentialsGrouper

    @Inject
    lateinit var autofillSelectCredentialsListBuilder: AutofillSelectCredentialsListBuilder

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            // If being created after a configuration change, dismiss the dialog as the WebView will be re-created too
            dismiss()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        pixelNameDialogEvent(Shown)?.let { pixel.fire(it) }

        val binding = ContentAutofillSelectCredentialsTooltipBinding.inflate(inflater, container, false)
        configureViews(binding)
        return binding.root
    }

    private fun configureViews(binding: ContentAutofillSelectCredentialsTooltipBinding) {
        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        configureRecyclerView(getUrlRequest(), binding)
        configureCloseButton(binding)
    }

    private fun configureCloseButton(binding: ContentAutofillSelectCredentialsTooltipBinding) {
        binding.closeButton.setOnClickListener { (dialog as BottomSheetDialog).animateClosed() }
    }

    private fun configureRecyclerView(
        autofillWebMessageRequest: AutofillWebMessageRequest,
        binding: ContentAutofillSelectCredentialsTooltipBinding,
    ) {
        binding.availableCredentialsRecycler.adapter = configureAdapter(getAvailableCredentials(autofillWebMessageRequest))
    }

    private fun configureAdapter(credentials: List<ListItem>): CredentialsPickerRecyclerAdapter {
        return CredentialsPickerRecyclerAdapter(
            lifecycleOwner = this,
            faviconManager = faviconManager,
            credentialTextExtractor = CredentialTextExtractor(requireContext()),
            listItems = credentials,
        ) { selectedCredentials ->

            pixelNameDialogEvent(Selected)?.let { pixel.fire(it) }

            val result = Bundle().also {
                it.putBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED, false)
                it.putParcelable(KEY_URL_REQUEST, getUrlRequest())
                it.putParcelable(KEY_CREDENTIALS, selectedCredentials)
            }
            parentFragment?.setFragmentResult(CredentialAutofillPickerDialog.resultKey(getTabId()), result)

            ignoreCancellationEvents = true
            dismiss()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        if (ignoreCancellationEvents) {
            Timber.v("onCancel: Ignoring cancellation event")
            return
        }

        Timber.v("onCancel: AutofillSelectCredentialsDialogFragment. User declined to autofill credentials")

        pixelNameDialogEvent(Dismissed)?.let { pixel.fire(it) }

        val result = Bundle().also {
            it.putBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED, true)
            it.putParcelable(KEY_URL_REQUEST, getUrlRequest())
        }

        parentFragment?.setFragmentResult(CredentialAutofillPickerDialog.resultKey(getTabId()), result)
    }

    private fun pixelNameDialogEvent(dialogEvent: DialogEvent): AutofillPixelNames? {
        val autoPrompted = getTriggerType() == AUTOPROMPT

        return when (dialogEvent) {
            is Shown -> if (autoPrompted) AUTOFILL_SELECT_LOGIN_AUTOPROMPT_SHOWN else AUTOFILL_SELECT_LOGIN_PROMPT_SHOWN
            is Selected -> if (autoPrompted) AUTOFILL_SELECT_LOGIN_AUTOPROMPT_SELECTED else AUTOFILL_SELECT_LOGIN_PROMPT_SELECTED
            is Dismissed -> if (autoPrompted) AUTOFILL_SELECT_LOGIN_AUTOPROMPT_DISMISSED else AUTOFILL_SELECT_LOGIN_PROMPT_DISMISSED
            else -> null
        }
    }

    private interface DialogEvent {
        object Shown : DialogEvent
        object Dismissed : DialogEvent
        object Selected : DialogEvent
    }

    private fun getAvailableCredentials(autofillWebMessageRequest: AutofillWebMessageRequest): List<ListItem> {
        val unsortedCredentials = BundleCompat.getParcelableArrayList(requireArguments(), KEY_CREDENTIALS, LoginCredentials::class.java)!!
        val grouped = autofillSelectCredentialsGrouper.group(autofillWebMessageRequest.requestOrigin, unsortedCredentials)
        return autofillSelectCredentialsListBuilder.buildFlatList(grouped)
    }

    private fun getUrlRequest() = BundleCompat.getParcelable(requireArguments(), KEY_URL_REQUEST, AutofillWebMessageRequest::class.java)!!
    private fun getTriggerType() = arguments?.getSerializable(KEY_TRIGGER_TYPE) as LoginTriggerType
    private fun getTabId() = arguments?.getString(KEY_TAB_ID)!!

    companion object {

        fun instance(
            autofillWebMessageRequest: AutofillWebMessageRequest,
            credentials: List<LoginCredentials>,
            triggerType: LoginTriggerType,
            tabId: String,
        ): AutofillSelectCredentialsDialogFragment {
            val cr = ArrayList<LoginCredentials>(credentials)

            val fragment = AutofillSelectCredentialsDialogFragment()
            fragment.arguments =
                Bundle().also {
                    it.putParcelable(KEY_URL_REQUEST, autofillWebMessageRequest)
                    it.putParcelableArrayList(KEY_CREDENTIALS, cr)
                    it.putSerializable(KEY_TRIGGER_TYPE, triggerType)
                    it.putString(KEY_TAB_ID, tabId)
                }
            return fragment
        }
    }
}
