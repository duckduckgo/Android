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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.global.FragmentViewModelFactory
import com.duckduckgo.autofill.impl.databinding.FragmentAutofillManagementLockedBinding
import com.duckduckgo.autofill.ui.credential.management.AutofillSettingsViewModel
import com.duckduckgo.di.scopes.FragmentScope
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class AutofillManagementLockedMode : Fragment() {
    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val viewModel by lazy {
        ViewModelProvider(requireActivity(), viewModelFactory)[AutofillSettingsViewModel::class.java]
    }
    private lateinit var binding: FragmentAutofillManagementLockedBinding

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAutofillManagementLockedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.lockedIcon.setOnClickListener {
            viewModel.launchDeviceAuth()
        }
        binding.lockedText.setOnClickListener {
            viewModel.launchDeviceAuth()
        }
    }

    companion object {
        fun instance() = AutofillManagementLockedMode()
    }
}
