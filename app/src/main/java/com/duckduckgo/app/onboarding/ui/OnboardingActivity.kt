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

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager.OnPageChangeListener
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.view.ColorCombiner
import com.duckduckgo.app.home.HomeActivity
import kotlinx.android.synthetic.main.activity_onboarding.*
import javax.inject.Inject


class OnboardingActivity : DuckDuckGoActivity() {

    @Inject lateinit var viewModelFactory: ViewModelFactory
    private lateinit var viewPageAdapter: PagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        configurePager()

        viewModel.viewState.observe(this, Observer<OnboardingViewModel.ViewState> {
            it?.let { render(it) }
        })
    }

    private val viewModel: OnboardingViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(OnboardingViewModel::class.java)
    }

    override fun onResume() {
        updateColor(viewPageAdapter.color(this, viewPager.currentItem))
        super.onResume()
    }

    private fun render(viewState: OnboardingViewModel.ViewState) {
        if (viewState.showHome) {
            showHome()
        }
    }

    fun onContinueClicked(view: View) {
        val next = viewPager.currentItem + 1
        if (next < viewPager.adapter!!.count) {
            viewPager.currentItem = next
        } else {
            viewModel.onOnboardingDone()
        }
    }

    private fun showHome() {
        startActivity(HomeActivity.intent(this))
        finish()
    }

    private fun configurePager() {

        viewPageAdapter = PagerAdapter(supportFragmentManager)
        viewPager.adapter = viewPageAdapter

        viewPager.addOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                transitionToNewColor(positionOffset)
            }

            override fun onPageSelected(position: Int) {
            }
        })
    }

    private fun transitionToNewColor(positionOffset: Float) {
        if (positionOffset == 0.toFloat()) {
            return
        }
        updateColor(viewPageAdapter.offsetColor(this, positionOffset))
    }

    private fun updateColor(@ColorInt color: Int) {
        window.statusBarColor = color
        viewPager.setBackgroundColor(color)
    }

    class PagerAdapter(fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {

        private val colorCombiner = ColorCombiner()

        companion object {
            val pageCount = 2

            @ColorRes
            val firstColor = R.color.lighMuddyGreen

            @ColorRes
            val secondColor = R.color.lightWindowsBlue
        }

        override fun getCount(): Int {
            return pageCount
        }

        override fun getItem(position: Int): android.support.v4.app.Fragment? {
            return when (position) {
                0 -> ProtectDataPage()
                1 -> NoTracePage()
                else -> null
            }
        }

        @ColorInt
        @Suppress("deprecation")
        fun offsetColor(context: Context, positionOffset: Float): Int {
            val fromColor = context.resources.getColor(firstColor)
            val toColor = context.resources.getColor(secondColor)
            return colorCombiner.combine(fromColor, toColor, positionOffset)
        }

        @ColorInt
        @Suppress("deprecation")
        fun color(context: Context, currentPage: Int): Int {
            val color = if (currentPage == 0) firstColor else secondColor
            return context.resources.getColor(color)
        }
    }

    class ProtectDataPage : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.content_onboarding_protect_data, container, false)
        }
    }

    class NoTracePage : Fragment() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.content_onboarding_no_trace, container, false)
        }
    }
}