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
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.databinding.ActivityOnboardingBinding
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.R

@InjectWith(ActivityScope::class)
class OnboardingActivity : DuckDuckGoActivity() {

    private lateinit var viewPageAdapter: PagerAdapter

    private val viewModel: OnboardingViewModel by bindViewModel()

    private val binding: ActivityOnboardingBinding by viewBinding()

    private val viewPager
        get() = binding.viewPager

    private var forceLightTheme: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forceLightTheme = intent.getBooleanExtra(EXTRA_FORCE_LIGHT_THEME, true)
        val onboardingTheme = if (forceLightTheme) R.style.Theme_DuckDuckGo_Light else R.style.Theme_DuckDuckGo
        setTheme(onboardingTheme)
        setContentView(binding.root)
        configurePager()
    }

    fun onContinueClicked() {
        val next = viewPager.currentItem + 1
        if (next < viewPager.adapter!!.count) {
            viewPager.setCurrentItem(next, true)
        } else {
            onOnboardingDone()
        }
    }

    fun onOnboardingDone() {
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
        val currentPage = viewPager.currentItem
        if (currentPage == 0) {
            finish()
        } else {
            viewPager.setCurrentItem(currentPage - 1, true)
        }
    }

    companion object {

        private const val EXTRA_FORCE_LIGHT_THEME = "FORCE_LIGHT_THEME"

        fun intent(context: Context, forceLightTheme: Boolean): Intent {
            return Intent(context, OnboardingActivity::class.java).apply {
                putExtra(EXTRA_FORCE_LIGHT_THEME, forceLightTheme)
            }
        }
    }
}
