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

package com.duckduckgo.duckchat.impl.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
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
import com.duckduckgo.duckchat.impl.databinding.FragmentSearchInterstitialBinding
import com.duckduckgo.duckchat.impl.ui.inputscreen.Command
import com.duckduckgo.duckchat.impl.ui.inputscreen.Command.SwitchModeToChat
import com.duckduckgo.duckchat.impl.ui.inputscreen.Command.SwitchModeToSearch
import com.duckduckgo.duckchat.impl.ui.inputscreen.Command.UserSubmittedQuery
import com.duckduckgo.duckchat.impl.ui.inputscreen.InputScreenViewModel
import com.duckduckgo.navigation.api.getActivityParams
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.api.VoiceSearchLauncher
import com.duckduckgo.voice.api.VoiceSearchLauncher.Event.SearchCancelled
import com.duckduckgo.voice.api.VoiceSearchLauncher.Event.VoiceRecognitionSuccess
import com.duckduckgo.voice.api.VoiceSearchLauncher.Event.VoiceSearchDisabled
import com.duckduckgo.voice.api.VoiceSearchLauncher.Source.BROWSER
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class SearchInterstitialFragment : DuckDuckGoFragment(R.layout.fragment_search_interstitial) {

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

    private val binding: FragmentSearchInterstitialBinding by viewBinding()

    private val pageChangeCallback = object : OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            binding.duckChatOmnibar.selectTab(position)
        }
    }

    private lateinit var pagerAdapter: InputScreenPagerAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        configureViewPager()
        configureOmnibar()
        configureVoice()
        configureObservers()

        val params = requireActivity().intent.getActivityParams(SearchInterstitialActivityParams::class.java)
        params?.query?.let { query ->
            binding.duckChatOmnibar.duckChatInput.setText(query)
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val query = binding.duckChatOmnibar.duckChatInput.text.toString()
                    val data = Intent().putExtra(SearchInterstitialActivity.QUERY, query)
                    requireActivity().setResult(Activity.RESULT_CANCELED, data)
                    exitInterstitial()
                }
            },
        )

        binding.actionSend.setOnClickListener {
            binding.duckChatOmnibar.submitMessage()
        }
        binding.duckChatOmnibar.duckChatInput.post {
            showKeyboard(binding.duckChatOmnibar.duckChatInput)
        }
        binding.actionNewLine.setOnClickListener {
            binding.duckChatOmnibar.printNewLine()
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
            is UserSubmittedQuery -> binding.duckChatOmnibar.submitMessage(command.query)
            SwitchModeToSearch -> {
                binding.viewPager.setCurrentItem(0, false)
            }

            SwitchModeToChat -> {
                binding.viewPager.setCurrentItem(1, false)
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

    private fun configureOmnibar() = with(binding.duckChatOmnibar) {
        setContentId(R.id.viewPager)

        onSearchSent = { query ->
            val data = Intent().putExtra(SearchInterstitialActivity.QUERY, query)
            requireActivity().setResult(Activity.RESULT_OK, data)
            exitInterstitial()
        }
        onDuckChatSent = { query ->
            val data = Intent().putExtra(SearchInterstitialActivity.QUERY, query)
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
        }
        onDuckChatSelected = {
            binding.actionSend.icon = AppCompatResources.getDrawable(context, R.drawable.ic_arrow_up_24)
            binding.viewPager.setCurrentItem(1, true)
            viewModel.onChatSelected()
        }
        onSendMessageAvailable = { isAvailable ->
            binding.actionSend.isVisible = isAvailable
            viewModel.triggerAutocomplete(binding.duckChatOmnibar.duckChatInput.text.toString(), true, true)
        }
    }

    private fun configureVoice() {
        binding.actionVoice.setOnClickListener {
            voiceSearchLauncher.launch(requireActivity())
        }
        voiceSearchLauncher.registerResultsCallback(this, requireActivity(), BROWSER) {
            when (it) {
                is VoiceRecognitionSuccess -> {
                    binding.duckChatOmnibar.submitMessage(it.result)
                }
                is SearchCancelled -> {}
                is VoiceSearchDisabled -> {
                    binding.actionVoice.isVisible = false
                }
            }
        }
        binding.actionVoice.isVisible = voiceSearchAvailability.isVoiceSearchAvailable
    }

    private fun exitInterstitial() {
        hideKeyboard(binding.duckChatOmnibar.duckChatInput)
        binding.duckChatOmnibar.animateOmnibarFocusedState(false)
        requireActivity().supportFinishAfterTransition()
    }

    override fun onDestroyView() {
        binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback)
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        binding.actionVoice.isVisible = voiceSearchAvailability.isVoiceSearchAvailable
    }
}
