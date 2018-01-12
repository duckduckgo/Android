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

package com.duckduckgo.app.onboarding

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager.OnPageChangeListener
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.view.ColorCombiner
import kotlinx.android.synthetic.main.activity_onboarding.*


class OnboardingActivity : AppCompatActivity() {

    private val colorCombiner = ColorCombiner()

    companion object {
        val firstColor = R.color.lighMuddyGreen
        val secondColor = R.color.lightWindowsBlue
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        configurePager()
    }

    override fun onResume() {
        refreshPageColor()
        super.onResume()
    }

    private fun configurePager() {

        viewPager.adapter = PagerAdapter(supportFragmentManager)

        viewPager.addOnPageChangeListener(object : OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                if (positionOffset == 0.toFloat()) {
                    return
                }
                transitionToNewColor(positionOffset)
            }

            override fun onPageSelected(position: Int) {
            }
        })
    }

    @SuppressLint("NewApi")
    private fun refreshPageColor() {
        val resource = if (viewPager.currentItem == 0) firstColor else secondColor
        updateColor(getColor(resource))
    }

    @SuppressLint("NewApi")
    private fun transitionToNewColor(positionOffset: Float) {
        val fromColor = resources.getColor(firstColor)
        val toColor = resources.getColor(secondColor)
        updateColor(colorCombiner.combine(fromColor, toColor, positionOffset))
    }

    private fun updateColor(@ColorInt color: Int) {
        window.statusBarColor = color
        viewPager.setBackgroundColor(color)
    }

    class PagerAdapter(fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {

        companion object {
            val pageCount = 2
        }

        override fun getCount(): Int {
            return pageCount
        }

        override fun getItem(position: Int): android.support.v4.app.Fragment? {
            when (position) {
                0 -> return ProtectDataPage()
                1 -> return NoTracePage()
                else -> return null
            }
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