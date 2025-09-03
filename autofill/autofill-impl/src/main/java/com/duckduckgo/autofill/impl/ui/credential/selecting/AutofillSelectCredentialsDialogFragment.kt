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
import androidx.fragment.app.setFragmentResult
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autofill.api.CredentialAutofillPickerDialog
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
import com.duckduckgo.autofill.impl.pixel.AutofillPixelParameters.LAST_USED_PIXEL_KEY
import com.duckduckgo.autofill.impl.ui.credential.dialog.animateClosed
import com.duckduckgo.autofill.impl.ui.credential.selecting.AutofillSelectCredentialsDialogFragment.DialogEvent.Dismissed
import com.duckduckgo.autofill.impl.ui.credential.selecting.AutofillSelectCredentialsDialogFragment.DialogEvent.Selected
import com.duckduckgo.autofill.impl.ui.credential.selecting.AutofillSelectCredentialsDialogFragment.DialogEvent.Shown
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ListItem
import com.duckduckgo.autofill.store.AutofillPrefsStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority.VERBOSE
import logcat.logcat

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

    @Inject
    @AppCoroutineScope
    lateinit var appCoroutineScope: CoroutineScope

    @Inject
    lateinit var dispatchers: DispatcherProvider

    @Inject
    lateinit var autofillPrefsStore: AutofillPrefsStore

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
        val originalUrl = getOriginalUrl()
        configureRecyclerView(originalUrl, binding)
        configureCloseButton(binding)
    }

    private fun configureCloseButton(binding: ContentAutofillSelectCredentialsTooltipBinding) {
        binding.closeButton.setOnClickListener { (dialog as BottomSheetDialog).animateClosed() }
    }

    private fun configureRecyclerView(
        originalUrl: String,
        binding: ContentAutofillSelectCredentialsTooltipBinding,
    ) {
        binding.availableCredentialsRecycler.adapter = configureAdapter(getAvailableCredentials(originalUrl))
    }

    private fun configureAdapter(credentials: List<ListItem>): CredentialsPickerRecyclerAdapter {
        return CredentialsPickerRecyclerAdapter(
            lifecycleOwner = this,
            faviconManager = faviconManager,
            credentialTextExtractor = CredentialTextExtractor(requireContext()),
            listItems = credentials,
        ) { selectedCredentials ->

            pixelNameDialogEvent(Selected)?.let {
                appCoroutineScope.launch(dispatchers.io()) {
                    val lastUsed = autofillPrefsStore.dataLastAutofilledDate
                    val params = if (lastUsed == null) emptyMap() else mapOf(LAST_USED_PIXEL_KEY to lastUsed)
                    pixel.fire(it, parameters = params)
                }
            }

            val result = Bundle().also {
                it.putBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED, false)
                it.putString(CredentialAutofillPickerDialog.KEY_URL, getOriginalUrl())
                it.putParcelable(CredentialAutofillPickerDialog.KEY_CREDENTIALS, selectedCredentials)
            }
            parentFragment?.setFragmentResult(CredentialAutofillPickerDialog.resultKey(getTabId()), result)

            ignoreCancellationEvents = true
            dismiss()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        if (ignoreCancellationEvents) {
            logcat(VERBOSE) { "onCancel: Ignoring cancellation event" }
            return
        }

        logcat(VERBOSE) { "onCancel: AutofillSelectCredentialsDialogFragment. User declined to autofill credentials" }

        pixelNameDialogEvent(Dismissed)?.let { pixel.fire(it) }

        val result = Bundle().also {
            it.putBoolean(CredentialAutofillPickerDialog.KEY_CANCELLED, true)
            it.putString(CredentialAutofillPickerDialog.KEY_URL, getOriginalUrl())
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

    private fun getAvailableCredentials(originalUrl: String): List<ListItem> {
        val unsortedCredentials = arguments?.getParcelableArrayList<LoginCredentials>(CredentialAutofillPickerDialog.KEY_CREDENTIALS)!!
        val grouped = autofillSelectCredentialsGrouper.group(originalUrl, unsortedCredentials)
        return autofillSelectCredentialsListBuilder.buildFlatList(grouped)
    }

    private fun getOriginalUrl() = arguments?.getString(CredentialAutofillPickerDialog.KEY_URL)!!
    private fun getTriggerType() = arguments?.getSerializable(CredentialAutofillPickerDialog.KEY_TRIGGER_TYPE) as LoginTriggerType
    private fun getTabId() = arguments?.getString(CredentialAutofillPickerDialog.KEY_TAB_ID)!!

    companion object {

        fun instance(
            url: String,
            credentials: List<LoginCredentials>,
            triggerType: LoginTriggerType,
            tabId: String,
        ): AutofillSelectCredentialsDialogFragment {
            val cr = ArrayList<LoginCredentials>(credentials)

            val fragment = AutofillSelectCredentialsDialogFragment()
            fragment.arguments =
                Bundle().also {
                    it.putString(CredentialAutofillPickerDialog.KEY_URL, url)
                    it.putParcelableArrayList(CredentialAutofillPickerDialog.KEY_CREDENTIALS, cr)
                    it.putSerializable(CredentialAutofillPickerDialog.KEY_TRIGGER_TYPE, triggerType)
                    it.putString(CredentialAutofillPickerDialog.KEY_TAB_ID, tabId)
                }
            return fragment
        }
    }
}
