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
import com.duckduckgo.autofill.impl.databinding.FragmentAutofillManagementViewModeBinding
import com.duckduckgo.autofill.ui.credential.management.AutofillManagementRecyclerAdapter
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel.Command.*
import com.duckduckgo.di.scopes.FragmentScope
import dagger.android.support.AndroidSupportInjection
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class AutofillManagementViewMode : Fragment() {

    @Inject
    lateinit var faviconManager: FaviconManager

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

//    val viewModel by lazy {
//        ViewModelProvider(this, viewModelFactory)[AutofillSettingsViewModel::class.java]
//    }

    val viewModel by lazy {
        ViewModelProvider(requireActivity(), viewModelFactory)[AutofillSettingsViewModel::class.java]
    }

    private lateinit var binding: FragmentAutofillManagementViewModeBinding
    private lateinit var adapter: AutofillManagementRecyclerAdapter

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentAutofillManagementViewModeBinding.inflate(inflater, container, false)
        configureRecyclerView()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.viewState.collect { state ->
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
//        lifecycleScope.launch {
//            repeatOnLifecycle(Lifecycle.State.STARTED) {
//                while (isActive) {
//                    Timber.i("Adding new credential")
//                    viewModel.addCredentials(
//                        LoginCredentials(
//                            domain = "${System.currentTimeMillis()}.com",
//                            username = "aaa@duck.com",
//                            password = "${Random.nextInt(10_000_000)}"
//                        )
//                    )
//                    delay(1_000)
//                }
//            }
//        }

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
            AutofillManagementViewMode().apply {
                arguments = Bundle().apply {
                }
            }
    }
}
