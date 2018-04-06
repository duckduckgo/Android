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

import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewPager.OnPageChangeListener
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.view.ColorCombiner
import kotlinx.android.synthetic.main.activity_onboarding.*
import javax.inject.Inject


class OnboardingActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory
    private lateinit var viewPageAdapter: PagerAdapter

    private val viewModel: OnboardingViewModel by lazy {
        ViewModelProviders.of(this, viewModelFactory).get(OnboardingViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        configurePager()
    }

    override fun onResume() {
        updateColor(viewPageAdapter.color(this, viewPager.currentItem))
        super.onResume()
    }

    fun onContinueClicked(view: View) {
        val next = viewPager.currentItem + 1
        if (next < viewPager.adapter!!.count) {
            viewPager.setCurrentItem(next, true)
        }
    }

    fun onDoneClicked(view: View) {
        viewModel.onOnboardingDone()
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

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, OnboardingActivity::class.java)
        }
    }

    class PagerAdapter(fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {

        private val colorCombiner = ColorCombiner()

        override fun getCount(): Int {
            return pageCount
        }

        override fun getItem(position: Int): Fragment? {
            return when (position) {
                0 -> ProtectDataPage()
                1 -> NoTracePage()
                else -> null
            }
        }

        @ColorInt
        fun offsetColor(context: Context, positionOffset: Float): Int {
            val fromColor = ContextCompat.getColor(context, firstColor)
            val toColor = ContextCompat.getColor(context, secondColor)
            return colorCombiner.combine(fromColor, toColor, positionOffset)
        }

        @ColorInt
        fun color(context: Context, currentPage: Int): Int {
            val color = if (currentPage == 0) firstColor else secondColor
            return ContextCompat.getColor(context, color)
        }

        companion object {
            const val pageCount = 2

            @ColorRes
            val firstColor = R.color.lighOliveGreen

            @ColorRes
            val secondColor = R.color.powderBlue
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