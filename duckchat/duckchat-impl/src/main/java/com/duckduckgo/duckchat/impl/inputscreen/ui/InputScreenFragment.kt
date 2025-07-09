/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.inputscreen.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.FragmentViewModelFactory
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.common.utils.extensions.showKeyboard
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.FragmentInputScreenBinding
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.EditWithSelectedQuery
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SwitchModeToChat
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SwitchModeToSearch
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SwitchToTab
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.UserSubmittedQuery
import com.duckduckgo.duckchat.impl.inputscreen.ui.tabs.InputScreenPagerAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.InputScreenViewModel
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.api.VoiceSearchLauncher
import com.duckduckgo.voice.api.VoiceSearchLauncher.Event.SearchCancelled
import com.duckduckgo.voice.api.VoiceSearchLauncher.Event.VoiceRecognitionSuccess
import com.duckduckgo.voice.api.VoiceSearchLauncher.Event.VoiceSearchDisabled
import com.duckduckgo.voice.api.VoiceSearchLauncher.Source.BROWSER
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@InjectWith(FragmentScope::class)
class InputScreenFragment : DuckDuckGoFragment(R.layout.fragment_input_screen) {

    @Inject
    lateinit var duckChat: DuckChat

    @Inject
    lateinit var voiceSearchLauncher: VoiceSearchLauncher

    @Inject
    lateinit var voiceSearchAvailability: VoiceSearchAvailability

    @Inject
    lateinit var viewModelFactory: FragmentViewModelFactory

    private val viewModel: InputScreenViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[InputScreenViewModel::class.java]
    }

    private val binding: FragmentInputScreenBinding by viewBinding()

    private val pageChangeCallback = object : OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            binding.inputModeWidget.selectTab(position)
        }
    }

    private lateinit var pagerAdapter: InputScreenPagerAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configureViewPager()
        configureOmnibar()
        configureVoice()
        configureObservers()

        val params = requireActivity().intent.getActivityParams(InputScreenActivityParams::class.java)
        params?.query?.let { query ->
            binding.inputModeWidget.provideInitialText(query)
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val query = binding.inputModeWidget.text
                    val data = Intent().putExtra(InputScreenActivity.QUERY, query)
                    requireActivity().setResult(Activity.RESULT_CANCELED, data)
                    exitInterstitial()
                }
            },
        )

        binding.actionSend.setOnClickListener {
            binding.inputModeWidget.submitMessage()
        }
        binding.inputModeWidget.inputField.post {
            showKeyboard(binding.inputModeWidget.inputField)
        }
        binding.actionNewLine.setOnClickListener {
            binding.inputModeWidget.printNewLine()
        }
    }

    private fun configureObservers() {
        viewModel.command.observe(
            viewLifecycleOwner,
        ) {
            processCommand(it)
        }
    }

    private fun processCommand(command: Command) {
        when (command) {
            is UserSubmittedQuery -> binding.inputModeWidget.submitMessage(command.query)
            is EditWithSelectedQuery -> binding.inputModeWidget.text = command.query
            is SwitchToTab -> {
                val data = Intent().putExtra(InputScreenActivity.TAB_ID, command.tabId)
                requireActivity().setResult(Activity.RESULT_OK, data)
                exitInterstitial()
            }
            SwitchModeToSearch -> {
                // binding.viewPager.setCurrentItem(0, false)
            }

            SwitchModeToChat -> {
                // binding.viewPager.setCurrentItem(1, false)
            }

            else -> {
                // TODO handle other commands
            }
        }
    }

    private fun configureViewPager() {
        pagerAdapter = InputScreenPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.registerOnPageChangeCallback(pageChangeCallback)
    }

    private fun configureOmnibar() = with(binding.inputModeWidget) {
        setContentId(R.id.viewPager)

        onSearchSent = { query ->
            val data = Intent().putExtra(InputScreenActivity.QUERY, query)
            requireActivity().setResult(Activity.RESULT_OK, data)
            exitInterstitial()
        }
        onChatSent = { query ->
            val data = Intent().putExtra(InputScreenActivity.QUERY, query)
            requireActivity().setResult(Activity.RESULT_CANCELED, data)
            requireActivity().finish()
            duckChat.openDuckChatWithAutoPrompt(query)
        }
        onBack = {
            requireActivity().onBackPressed()
        }
        onSearchSelected = {
            binding.actionSend.icon = AppCompatResources.getDrawable(context, com.duckduckgo.mobile.android.R.drawable.ic_find_search_24)
            binding.viewPager.setCurrentItem(0, true)
            viewModel.onSearchSelected()
            viewModel.onSearchInputTextChanged(binding.inputModeWidget.text)
        }
        onChatSelected = {
            binding.actionSend.icon = AppCompatResources.getDrawable(context, R.drawable.ic_arrow_up_24)
            binding.viewPager.setCurrentItem(1, true)
            viewModel.onChatSelected()
        }
        onSendMessageAvailable = { isAvailable ->
            binding.actionSend.isVisible = isAvailable
            if (binding.viewPager.currentItem == 0) {
                viewModel.onSearchInputTextChanged(binding.inputModeWidget.text)
            }
        }
        onVoiceInputAllowed = { isAllowed ->
            viewModel.onVoiceInputAllowedChange(isAllowed)
        }
    }

    private fun configureVoice() {
        binding.actionVoice.setOnClickListener {
            voiceSearchLauncher.launch(requireActivity())
        }
        voiceSearchLauncher.registerResultsCallback(this, requireActivity(), BROWSER) {
            when (it) {
                is VoiceRecognitionSuccess -> {
                    binding.inputModeWidget.submitMessage(it.result)
                }
                is SearchCancelled -> {}
                is VoiceSearchDisabled -> {
                    viewModel.onVoiceSearchDisabled()
                }
            }
        }
        viewModel.visibilityState.onEach {
            binding.actionVoice.isInvisible = !it.voiceInputButtonVisible
            binding.actionForceWebSearch.isVisible = it.forceWebSearchButtonVisible
        }.launchIn(lifecycleScope)
    }

    private fun exitInterstitial() {
        hideKeyboard(binding.inputModeWidget.inputField)
        binding.inputModeWidget.animateOmnibarFocusedState(false)
        requireActivity().supportFinishAfterTransition()
    }

    override fun onDestroyView() {
        binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onActivityResume()
    }
}
