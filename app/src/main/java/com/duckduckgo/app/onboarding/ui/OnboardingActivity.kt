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
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import android.view.View
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.global.view.ColorCombiner
import com.duckduckgo.app.onboarding.ui.ColorChangingPageListener.NewColorListener
import kotlinx.android.synthetic.main.activity_onboarding.*
import javax.inject.Inject


class OnboardingActivity : DuckDuckGoActivity() {

    @Inject
    lateinit var colorCombiner: ColorCombiner

    private lateinit var viewPageAdapter: PagerAdapter

    private val viewModel: OnboardingViewModel by bindViewModel()

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

    private fun configurePager() {

        viewPageAdapter = PagerAdapter(supportFragmentManager, viewModel)
        viewPager.adapter = viewPageAdapter
        val pageListener = ColorChangingPageListener(colorCombiner, object : NewColorListener {
            override fun update(@ColorInt color: Int) = updateColor(color)
            override fun getColorForPage(position: Int): Int? {
                val color = viewPageAdapter.getItem(position)?.backgroundColor() ?: return null
                return ContextCompat.getColor(this@OnboardingActivity, color)
            }
        })

        viewPager.addOnPageChangeListener(pageListener)
    }

    private fun updateColor(@ColorInt color: Int) {
        viewPager.setBackgroundColor(color)
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, OnboardingActivity::class.java)
        }
    }
}