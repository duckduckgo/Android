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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.FragmentViewModelFactory
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.databinding.FragmentAutofillManagementEditModeBinding
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.Command
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.Command.*
import com.duckduckgo.di.scopes.FragmentScope
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.fragment_autofill_management_edit_mode.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class AutofillManagementEditMode : Fragment() {

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    val viewModel by lazy {
        ViewModelProvider(requireActivity(), viewModelFactory)[AutofillSettingsViewModel::class.java]
    }

    private lateinit var binding: FragmentAutofillManagementEditModeBinding

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentAutofillManagementEditModeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
        populateFields(getCredentials())
        configureUiEventHandlers()
    }

    private fun configureUiEventHandlers() {
        binding.saveButton.setOnClickListener { saveCredentials() }
        binding.deleteButton.setOnClickListener { deleteCredentials() }
        binding.copyUsernameButton.setOnClickListener { copyUsername() }
        binding.copyPasswordButton.setOnClickListener { copyPassword() }
    }

    private fun copyUsername() {
        viewModel.onCopyUsername(binding.usernameEditText.text.toString())
    }

    private fun copyPassword() {
        viewModel.onCopyPassword(binding.passwordEditText.text.toString())
    }

    private fun saveCredentials() {
        val updatedCredentials = getCredentials().copy(
            username = binding.usernameEditText.text.toString(),
            password = binding.passwordEditText.text.toString(),
            // domain = binding.,
            // title = binding.titleEditText.text.toString()
        )
        viewModel.updateCredentials(updatedCredentials)
    }

    private fun deleteCredentials() {
        viewModel.onDeleteCredentials(getCredentials())
    }

    private fun populateFields(credentials: LoginCredentials) {
        binding.usernameEditText.setText(credentials.username)
        binding.passwordEditText.setText(credentials.password)
        binding.domainEditText.setText(credentials.domain)
    }

    private fun getCredentials(): LoginCredentials {
        return requireArguments().getParcelable("creds")!!
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.collect { state ->
                }
            }
        }
    }

    private fun processCommand(command: Command) {
        var processed = true
        when (command) {
            else -> processed = false
        }
        if (processed) {
            Timber.v("Processed command $command")
            viewModel.commandProcessed(command)
        }
    }

    companion object {
        fun instance(credentials: LoginCredentials) =
            AutofillManagementEditMode().apply {
                arguments = Bundle().apply {
                    putParcelable("creds", credentials)
                }
            }
    }
}
