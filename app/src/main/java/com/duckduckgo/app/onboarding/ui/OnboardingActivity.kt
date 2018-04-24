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
import android.os.Build
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.annotation.RequiresApi
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewPager
import android.support.v4.view.ViewPager.OnPageChangeListener
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.ViewModelFactory
import com.duckduckgo.app.global.view.ColorCombiner
import com.duckduckgo.app.global.view.launchDefaultAppActivity
import kotlinx.android.synthetic.main.activity_onboarding.*
import timber.log.Timber
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
        } else {
            viewModel.onOnboardingDone()
            finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun onLaunchDefaultBrowserSettingsClicked(view: View) {
        launchDefaultAppActivity()
    }

    private fun configurePager() {

        viewPageAdapter = PagerAdapter(supportFragmentManager, viewModel)
        viewPager.adapter = viewPageAdapter

        viewPager.addOnPageChangeListener(object : OnPageChangeListener {

            var previousPosition = viewPager.currentItem
            var previousOffset = 0.0f

            var currentPosition = 0

            var nextPage: OnboardingPageFragment? = null
            var scrollDirection: ScrollDirection = ScrollDirection.None
            var scrollState: Int = ViewPager.SCROLL_STATE_IDLE

            override fun onPageScrollStateChanged(state: Int) {
                scrollState = state
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                val scrolling = positionOffset != 0f
                val scrollingForwards = positionOffset > previousOffset

                //val scrollingForwards = position == previousPosition
                previousOffset = positionOffset

                scrollDirection = if (!scrolling) {
                    nextPage = null
                    ScrollDirection.None
                } else if (scrollingForwards) {
                    ScrollDirection.Right
                } else {
                    ScrollDirection.Left
                }

                if(scrollState != ViewPager.SCROLL_STATE_SETTLING) {
                    nextPage = when (scrollDirection) {
                        ScrollDirection.Right -> viewPageAdapter.getItem(position + 1)
                        ScrollDirection.Left -> viewPageAdapter.getItem(currentPosition - 1)
                        ScrollDirection.None -> null
                    }
                }

                val currentPage = viewPageAdapter.getItem(viewPager.currentItem)
                Timber.i("onPageScrolled - scrolling $scrollDirection, current=$currentPage, nextPage=$nextPage")

                val modifiedOffset = if(scrollDirection == ScrollDirection.Left) 1-positionOffset else positionOffset
                transitionToNewColor(modifiedOffset, currentPage, nextPage)

            }

            override fun onPageSelected(position: Int) {
                Timber.i("onPageSelected $position")
                currentPosition = position
                scrollDirection = ScrollDirection.None
                previousPosition = position
                nextPage = null
            }
        })
    }

    sealed class ScrollDirection {
        object Left : ScrollDirection()
        object Right : ScrollDirection()
        object None : ScrollDirection()

        override fun toString(): String {
            return javaClass.simpleName
        }
    }

    private fun transitionToNewColor(positionOffset: Float, currentPage: OnboardingPageFragment?, nextPage: OnboardingPageFragment?) {
        if(positionOffset == 0f || (currentPage == null && nextPage == null)) {
            return
        }

        val normalisedCurrentPage = currentPage?: nextPage!!
        val normalisedNextPage = nextPage?: currentPage!!

        val newColor = viewPageAdapter.offsetColor(this, normalisedCurrentPage, normalisedNextPage, positionOffset)
        updateColor(newColor)
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

    class PagerAdapter(fragmentManager: FragmentManager, private val viewModel: OnboardingViewModel) : FragmentPagerAdapter(fragmentManager) {

        private val colorCombiner = ColorCombiner()

        override fun getCount(): Int {
            return viewModel.pageCount()
        }

        override fun getItem(position: Int): OnboardingPageFragment? {
            return viewModel.getItem(position)
        }

        @ColorInt
        fun offsetColor(context: Context,
                        currentPage: OnboardingPageFragment,
                        nextPage: OnboardingPageFragment,
                        positionOffset: Float) : Int {
            val fromColor = ContextCompat.getColor(context, currentPage.backgroundColor())
            val toColor = ContextCompat.getColor(context, nextPage.backgroundColor())

            return colorCombiner.combine(fromColor, toColor, positionOffset)
        }

        @ColorInt
        fun color(context: Context, currentPage: Int): Int {
            val color = getItem(currentPage)?.backgroundColor() ?: R.color.lightOliveGreen
            return ContextCompat.getColor(context, color)
        }
    }

    class ProtectDataPage : OnboardingPageFragment() {

        init {
            type = "ProtectedData"
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.content_onboarding_protect_data, container, false)
        }

        override fun backgroundColor(): Int = R.color.lightOliveGreen
    }

    class NoTracePage : OnboardingPageFragment() {

        init {
            type = "NoTrace"
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.content_onboarding_no_trace, container, false)
        }

        override fun backgroundColor(): Int = R.color.powderBlue
    }

    class DefaultBrowserPage : OnboardingPageFragment() {

        init {
            type = "DefaultBrowser"
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            return inflater.inflate(R.layout.content_onboarding_default_browser, container, false)
        }

        override fun backgroundColor(): Int = R.color.eastBay
    }

    abstract class OnboardingPageFragment: Fragment() {

        open lateinit var type: String

        @ColorRes
        abstract fun backgroundColor(): Int
    }
}