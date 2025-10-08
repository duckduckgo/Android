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

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.FragmentIntroSyncBinding
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen
import com.duckduckgo.sync.impl.ui.setup.SyncSetupIntroViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.SyncSetupIntroViewModel.Command.AbortFlow
import com.duckduckgo.sync.impl.ui.setup.SyncSetupIntroViewModel.Command.RecoverDataFlow
import com.duckduckgo.sync.impl.ui.setup.SyncSetupIntroViewModel.Command.StartSetupFlow
import com.duckduckgo.sync.impl.ui.setup.SyncSetupIntroViewModel.ViewMode.CreateAccountIntro
import com.duckduckgo.sync.impl.ui.setup.SyncSetupIntroViewModel.ViewMode.RecoverAccountIntro
import com.duckduckgo.sync.impl.ui.setup.SyncSetupIntroViewModel.ViewState
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.logcat
import javax.inject.*

@InjectWith(FragmentScope::class)
class SyncSetupIntroFragment : DuckDuckGoFragment(R.layout.fragment_intro_sync) {
    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val binding: FragmentIntroSyncBinding by viewBinding()

    private val viewModel: SyncSetupIntroViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[SyncSetupIntroViewModel::class.java]
    }

    private val listener: SyncSetupNavigationFlowListener?
        get() = activity as? SyncSetupNavigationFlowListener

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        configureListeners()
        observeUiEvents()
    }

    private fun configureListeners() {
        binding.closeIcon.setOnClickListener {
            viewModel.onAbortClicked()
        }
    }

    private fun observeUiEvents() {
        viewModel
            .viewState(getScreen())
            .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
            .onEach { viewState -> renderViewState(viewState) }
            .launchIn(lifecycleScope)

        viewModel
            .commands()
            .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
            .onEach { processCommand(it) }
            .launchIn(lifecycleScope)
    }

    private fun renderViewState(viewState: ViewState) {
        when (viewState.viewMode) {
            is CreateAccountIntro -> {
                binding.contentTitle.text = getString(R.string.sync_intro_enable_title)
                binding.contentBody.text = getString(R.string.sync_intro_enable_content)
                binding.contentIllustration.setImageResource(R.drawable.ic_sync_server_128)
                binding.syncIntroCta.text = getString(R.string.sync_intro_enable_cta)
                binding.syncIntroFooter.text = getString(R.string.sync_intro_enable_footer)
                binding.syncIntroFooter.show()
                binding.syncIntroCta.setOnClickListener {
                    viewModel.onTurnSyncOnClicked()
                }
            }

            is RecoverAccountIntro -> {
                binding.contentTitle.text = getString(R.string.sync_intro_recover_title)
                binding.contentBody.text = getString(R.string.sync_intro_recover_content)
                binding.contentIllustration.setImageResource(R.drawable.ic_sync_recover_128)
                binding.syncIntroFooter.hide()
                binding.syncIntroCta.text = getString(R.string.sync_intro_recover_cta)
                binding.syncIntroCta.setOnClickListener {
                    viewModel.onStartRecoverDataClicked()
                }
            }
        }
    }

    private fun processCommand(it: Command) {
        when (it) {
            AbortFlow -> {
                requireActivity().setResult(Activity.RESULT_CANCELED)
                requireActivity().finish()
            }
            StartSetupFlow -> listener?.launchCreateAccountScreen()
            RecoverDataFlow -> listener?.launchRecoverAccountScreen()
        }
    }

    private fun getScreen(): Screen {
        return requireArguments().getSerializable(KEY_CREATE_ACCOUNT_INTRO) as Screen
    }

    companion object {

        const val KEY_CREATE_ACCOUNT_INTRO = "KEY_CREATE_ACCOUNT_INTRO"

        fun instance(screen: Screen): SyncSetupIntroFragment {
            logcat { "Sync-Setup: screen $screen" }
            val fragment = SyncSetupIntroFragment()
            val bundle = Bundle()
            bundle.putSerializable(KEY_CREATE_ACCOUNT_INTRO, screen)
            fragment.arguments = bundle
            return fragment
        }
    }
}
