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

package com.duckduckgo.autofill.ui.credential.management.viewing

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.RadioGroup.OnCheckedChangeListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.FragmentViewModelFactory
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.databinding.FragmentAutofillManagementListModeBinding
import com.duckduckgo.autofill.ui.credential.management.AutofillManagementRecyclerAdapter
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.Command.*
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.ui.view.quietlySetIsChecked
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class AutofillManagementListMode : Fragment() {

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    val viewModel by lazy {
        ViewModelProvider(requireActivity(), viewModelFactory)[AutofillSettingsViewModel::class.java]
    }

    private lateinit var binding: FragmentAutofillManagementListModeBinding
    private lateinit var adapter: AutofillManagementRecyclerAdapter

    private val globalAutofillToggleListener = object : CompoundButton.OnCheckedChangeListener {

        override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
            if (isChecked) viewModel.onEnableAutofill() else viewModel.onDisableAutofill()
        }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentAutofillManagementListModeBinding.inflate(inflater, container, false)
        configureToggle()
        configureRecyclerView()
        return binding.root
    }

    private fun configureToggle() {
        // binding.enabledToggle.isEnabled = false
        binding.enabledToggle.setOnCheckedChangeListener(globalAutofillToggleListener)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.collect { state ->
                    binding.enabledToggle.quietlySetIsChecked(state.autofillEnabled, globalAutofillToggleListener)
                    credentialsListUpdated(state.logins)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.commands.collect { commands ->
                    commands.forEach { processCommand(it) }
                }
            }
        }

        viewModel.observeCredentials()
    }

    private fun processCommand(command: AutofillSettingsViewModel.Command) {
        var processed = true
        when (command) {
            else -> processed = false
        }
        if (processed) {
            Timber.v("Processed command $command")
            viewModel.commandProcessed(command)
        }
    }

    private fun credentialsListUpdated(credentials: List<LoginCredentials>) {
        adapter.updateLogins(credentials)
    }

    private fun configureRecyclerView() {
        adapter = AutofillManagementRecyclerAdapter(
            this, faviconManager,
            onCredentialSelected = this::onCredentialsSelected,
            onCopyUsername = this::onCopyUsername,
            onCopyPassword = this::onCopyPassword,
        )

        binding.logins.adapter = adapter
    }

    private fun onCredentialsSelected(credentials: LoginCredentials) {
        viewModel.onEditCredentials(credentials)
    }

    private fun onCopyUsername(credentials: LoginCredentials) {
        viewModel.onCopyUsername(credentials.username)
    }

    private fun onCopyPassword(credentials: LoginCredentials) {
        viewModel.onCopyPassword(credentials.password)
    }

    companion object {
        fun instance() =
            AutofillManagementListMode().apply {
                arguments = Bundle().apply {
                }
            }
    }
}
