/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.store.AppTheme
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.R.style.Theme_DuckDuckGo_Light
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.databinding.FragmentCreateAccountBinding
import com.duckduckgo.sync.impl.ui.setup.SyncSetupDeepLinkViewModel.ViewMode.CreatingAccount
import com.duckduckgo.sync.impl.ui.setup.SyncSetupDeepLinkViewModel.ViewState
import javax.inject.*
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(FragmentScope::class)
class SyncSetupDeepLinkFragment : DuckDuckGoFragment() {

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    @Inject
    lateinit var appTheme: AppTheme

    @Inject
    lateinit var syncFeature: SyncFeature

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        /*
         * This fragment can be used in fragments which have a hardcoded dark theme.
         * We want to be able to override that for this fragment and use a light theme if the app is set to use light theme.
         */
        (activity as? AppCompatActivity)?.supportActionBar?.hide()

        if (appTheme.isLightModeEnabled() && syncFeature.canOverrideThemeSyncSetup().isEnabled()) {
            val themeAwareInflater = inflater.cloneInContext(ContextThemeWrapper(requireContext(), Theme_DuckDuckGo_Light))
            return themeAwareInflater.inflate(R.layout.fragment_create_account, container, false)
        } else {
            return inflater.inflate(R.layout.fragment_create_account, container, false)
        }
    }

    private val binding: FragmentCreateAccountBinding by viewBinding()

    private val viewModel: SyncSetupDeepLinkViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[SyncSetupDeepLinkViewModel::class.java]
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        observeUiEvents()
    }

    private fun observeUiEvents() {
        viewModel
            .viewState()
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        when (viewState.viewMode) {
            is CreatingAccount -> {
                binding.connecting.show()
            }
        }
    }

    companion object {
        fun instance(): Fragment {
            return SyncSetupDeepLinkFragment().also {
                it.arguments = Bundle()
            }
        }
    }
}
