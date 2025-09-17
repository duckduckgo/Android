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
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.ui.DuckDuckGoFragment
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.common.utils.extensions.hideKeyboard
import com.duckduckgo.common.utils.extensions.showKeyboard
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.api.inputscreen.InputScreenActivityParams
import com.duckduckgo.duckchat.api.inputscreen.InputScreenActivityResultCodes
import com.duckduckgo.duckchat.api.inputscreen.InputScreenActivityResultParams
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.FragmentInputScreenBinding
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.EditWithSelectedQuery
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.HideKeyboard
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.ShowKeyboard
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SubmitChat
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SubmitSearch
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SwitchToTab
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.UserSubmittedQuery
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.InputFieldCommand
import com.duckduckgo.duckchat.impl.inputscreen.ui.state.SubmitButtonIcon.SEARCH
import com.duckduckgo.duckchat.impl.inputscreen.ui.state.SubmitButtonIcon.SEND
import com.duckduckgo.duckchat.impl.inputscreen.ui.tabs.InputScreenPagerAdapter
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.InputScreenViewModel
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.InputScreenViewModel.InputScreenViewModelFactory
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.InputScreenViewModel.InputScreenViewModelProviderFactory
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
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
    lateinit var voiceSearchLauncher: VoiceSearchLauncher

    @Inject
    lateinit var voiceSearchAvailability: VoiceSearchAvailability

    @Inject
    lateinit var pixel: Pixel

    @Inject
    lateinit var viewModelFactory: InputScreenViewModelFactory

    private val viewModel: InputScreenViewModel by lazy {
        val params = requireActivity().intent.getActivityParams(InputScreenActivityParams::class.java)
        val currentOmnibarText = params?.query ?: ""
        val providerFactory = InputScreenViewModelProviderFactory(viewModelFactory, currentOmnibarText = currentOmnibarText)
        ViewModelProvider(owner = this, factory = providerFactory)[InputScreenViewModel::class.java]
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

        val params = requireActivity().intent.getActivityParams(InputScreenActivityParams::class.java)
        params?.query?.let { query ->
            binding.inputModeWidget.provideInitialText(query)
        }

        configureViewPager()
        configureOmnibar()
        configureVoice()
        configureObservers()

        binding.inputModeWidget.init()

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val query = binding.inputModeWidget.text
                    val data = Intent().putExtra(InputScreenActivityResultParams.CANCELED_DRAFT_PARAM, query)
                    requireActivity().setResult(Activity.RESULT_CANCELED, data)
                    exitInputScreen()
                }
            },
        )

        binding.actionSend.setOnClickListener {
            // todo remove round-tripping through the input mode widget - actions should go directly to the view model
            binding.inputModeWidget.submitMessage()
            viewModel.onSendButtonClicked()
        }
        binding.inputModeWidget.inputField.post {
            showKeyboard(binding.inputModeWidget.inputField)
        }
        binding.actionNewLine.setOnClickListener {
            binding.inputModeWidget.printNewLine()
            pixel.fire(DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_FLOATING_RETURN_PRESSED)
        }

        viewModel.fireShownPixel()
    }

    private fun configureObservers() {
        viewModel.command.observe(
            viewLifecycleOwner,
        ) {
            processCommand(it)
        }

        viewModel.inputFieldCommand.onEach { command ->
            when (command) {
                is InputFieldCommand.SelectAll -> {
                    binding.inputModeWidget.selectAllText()
                }
            }
        }.launchIn(lifecycleScope)

        viewModel.submitButtonIconState.onEach { iconState ->
            val iconResource = when (iconState.icon) {
                SEARCH -> com.duckduckgo.mobile.android.R.drawable.ic_find_search_24
                SEND -> com.duckduckgo.mobile.android.R.drawable.ic_arrow_right_24
            }
            binding.actionSend.setImageResource(iconResource)
        }.launchIn(lifecycleScope)

        viewModel.inputFieldState.onEach { inputBoxState ->
            binding.inputModeWidget.canExpand = inputBoxState.canExpand
        }.launchIn(lifecycleScope)

        viewModel.visibilityState.onEach {
            binding.ddgLogo.isVisible = if (binding.viewPager.currentItem == 0) {
                it.showSearchLogo
            } else {
                it.showChatLogo
            }
            binding.actionNewLine.isVisible = it.newLineButtonVisible
        }.launchIn(lifecycleScope)
    }

    private fun processCommand(command: Command) {
        when (command) {
            is UserSubmittedQuery -> binding.inputModeWidget.submitMessage(command.query)
            is EditWithSelectedQuery -> binding.inputModeWidget.text = command.query
            is SwitchToTab -> {
                val data = Intent().putExtra(InputScreenActivityResultParams.TAB_ID_PARAM, command.tabId)
                requireActivity().setResult(InputScreenActivityResultCodes.SWITCH_TO_TAB_REQUESTED, data)
                exitInputScreen()
            }
            is SubmitSearch -> submitSearchQuery(command.query)
            is SubmitChat -> submitChatQuery(command.query)
            is ShowKeyboard -> showKeyboard(binding.inputModeWidget.inputField)
            is HideKeyboard -> hideKeyboard(binding.inputModeWidget.inputField)
        }
    }

    private fun configureViewPager() {
        pagerAdapter = InputScreenPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.registerOnPageChangeCallback(pageChangeCallback)
    }

    private fun configureOmnibar() = with(binding.inputModeWidget) {
        onSearchSent = { query ->
            viewModel.onSearchSubmitted(query)
        }
        onChatSent = { query ->
            viewModel.onChatSubmitted(query)
        }
        onBack = {
            requireActivity().onBackPressed()
        }
        onSearchSelected = {
            binding.viewPager.setCurrentItem(0, true)
            viewModel.onSearchSelected()
            viewModel.onSearchInputTextChanged(binding.inputModeWidget.text)
            binding.ddgLogo.apply {
                setImageResource(com.duckduckgo.mobile.android.R.drawable.logo_full)
                isVisible = viewModel.visibilityState.value.showSearchLogo
            }
        }
        onChatSelected = {
            binding.viewPager.setCurrentItem(1, true)
            viewModel.onChatSelected()
            viewModel.onChatInputTextChanged(binding.inputModeWidget.text)
            binding.ddgLogo.apply {
                setImageResource(R.drawable.logo_full_ai)
                val showChatLogo = viewModel.visibilityState.value.showChatLogo
                val showSearchLogo = viewModel.visibilityState.value.showSearchLogo
                isVisible = showChatLogo
                if (showChatLogo && !showSearchLogo) {
                    alpha = 0f
                    animate().alpha(1f).setDuration(200L).start()
                }
            }
        }
        onSubmitMessageAvailable = { isAvailable ->
            binding.actionSend.isVisible = isAvailable
        }
        onVoiceInputAllowed = { isAllowed ->
            viewModel.onVoiceInputAllowedChange(isAllowed)
        }
        onSearchTextChanged = { text ->
            viewModel.onSearchInputTextChanged(text)
        }
        onChatTextChanged = { text ->
            viewModel.onChatInputTextChanged(text)
        }
        onInputFieldClicked = {
            viewModel.onInputFieldTouched()
        }
    }

    private fun submitChatQuery(query: String) {
        val data = Intent().putExtra(InputScreenActivityResultParams.CANCELED_DRAFT_PARAM, query)
        requireActivity().setResult(Activity.RESULT_CANCELED, data)
        requireActivity().finish()
    }

    private fun submitSearchQuery(query: String) {
        val data = Intent().putExtra(InputScreenActivityResultParams.SEARCH_QUERY_PARAM, query)
        requireActivity().setResult(InputScreenActivityResultCodes.NEW_SEARCH_REQUESTED, data)
        exitInputScreen()
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
        }.launchIn(lifecycleScope)
    }

    private fun exitInputScreen() {
        hideKeyboard(binding.inputModeWidget.inputField)
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
