/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.databinding.ActivityOnboardingBinding
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@InjectWith(ActivityScope::class)
class OnboardingActivity : DuckDuckGoActivity() {

    private lateinit var viewPageAdapter: PagerAdapter

    private val viewModel: OnboardingViewModel by bindViewModel()

    private val binding: ActivityOnboardingBinding by viewBinding()

    private val viewPager
        get() = binding.viewPager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        configurePager()
        configureSkipButton()
        observeViewModel()
    }

    fun onContinueClicked() {
        val next = viewPager.currentItem + 1
        if (next < viewPager.adapter!!.count) {
            viewPager.setCurrentItem(next, true)
        } else {
            onOnboardingDone()
        }
    }

    fun onSkipClicked() {
        viewModel.onOnboardingSkipped()
        startActivity(BrowserActivity.intent(this@OnboardingActivity))
        finish()
    }

    private fun onOnboardingDone() {
        viewModel.onOnboardingDone()
        startActivity(BrowserActivity.intent(this@OnboardingActivity))
        finish()
    }

    private fun configurePager() {
        viewModel.initializePages()

        viewPageAdapter = PagerAdapter(supportFragmentManager, viewModel)
        viewPager.offscreenPageLimit = 1
        viewPager.adapter = viewPageAdapter
        viewPager.setSwipingEnabled(false)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        val currentPage = viewPager.currentItem
        if (currentPage == 0) {
            finish()
        } else {
            viewPager.setCurrentItem(currentPage - 1, true)
        }
    }

    private fun observeViewModel() {
        viewModel.viewState.flowWithLifecycle(lifecycle, STARTED)
            .onEach {
                if (it.canShowSkipOnboardingButton) {
                    binding.skipOnboardingButton.show()
                } else {
                    binding.skipOnboardingButton.gone()
                }
            }
            .launchIn(lifecycleScope)
    }

    private fun configureSkipButton() {
        binding.skipOnboardingButton.setOnClickListener {
            lifecycleScope.launch {
                viewModel.devOnlyFullyCompleteAllOnboarding()
                startActivity(BrowserActivity.intent(this@OnboardingActivity))
                finish()
            }
        }
        viewModel.initializeOnboardingSkipper()
    }

    companion object {

        fun intent(context: Context): Intent {
            return Intent(context, OnboardingActivity::class.java)
        }
    }
}
