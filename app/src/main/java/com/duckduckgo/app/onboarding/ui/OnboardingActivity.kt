/*
 * Copyright (c) 2018 DuckDuckGo
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
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.onboarding.ui.page.TrackerBlockerOptInPage
import kotlinx.android.synthetic.main.activity_onboarding.*


class OnboardingActivity : DuckDuckGoActivity(), TrackerBlockerOptInPage.TrackerBlockingDecisionListener {

    private lateinit var viewPageAdapter: PagerAdapter

    private val viewModel: OnboardingViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        configurePager(intent.getBooleanExtra(IS_FRESH_INSTALL_EXTRA, true))
    }

    fun onContinueClicked() {
        val next = viewPager.currentItem + 1
        if (next < viewPager.adapter!!.count) {
            viewPager.setCurrentItem(next, true)
        } else {
            viewModel.onOnboardingDone()
            startActivity(BrowserActivity.intent(this))
            finish()
        }
    }

    private fun configurePager(isFreshAppInstall: Boolean) {
        viewModel.initializePages(isFreshAppInstall)

        viewPageAdapter = PagerAdapter(supportFragmentManager, viewModel)
        viewPager.offscreenPageLimit = 1
        viewPager.adapter = viewPageAdapter
        viewPager.setSwipingEnabled(false)
    }

    override fun onUserEnabledTrackerBlocking() {
        viewPageAdapter.notifyDataSetChanged()
        onContinueClicked()
    }

    override fun onUserDisabledTrackerBlocking() {
        viewPageAdapter.notifyDataSetChanged()
        onContinueClicked()
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

        private const val IS_FRESH_INSTALL_EXTRA = "IS_FRESH_INSTALL_EXTRA"

        fun intent(context: Context, isFreshAppInstall: Boolean): Intent {
            val intent = Intent(context, OnboardingActivity::class.java)
            intent.putExtra(IS_FRESH_INSTALL_EXTRA, isFreshAppInstall)
            return intent
        }
    }
}