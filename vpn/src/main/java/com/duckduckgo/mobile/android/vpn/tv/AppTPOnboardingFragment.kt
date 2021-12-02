/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.tv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.leanback.app.OnboardingSupportFragment
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.ui.onboarding.DeviceShieldOnboardingViewModel

class AppTPOnboardingFragment: OnboardingSupportFragment() {

    override fun getPageCount(): Int {
        return pages.size
    }

    override fun getPageTitle(pageIndex: Int): CharSequence {
       return getString(pages[pageIndex].title)
    }

    override fun getPageDescription(pageIndex: Int): CharSequence {
        return getString(pages[pageIndex].text)
    }

    override fun onCreateBackgroundView(inflater: LayoutInflater?, container: ViewGroup?): View? {
        return null
    }

    override fun onCreateContentView(inflater: LayoutInflater?, container: ViewGroup?): View? {
        return ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setImageResource(R.drawable.device_shield_onboarding_page_three_header)
            setPadding(32, 32, 32, 32)
        }
    }

    override fun onCreateForegroundView(inflater: LayoutInflater?, container: ViewGroup?): View? {
        return null
    }

    companion object {
        data class OnboardingPage(val imageHeader: Int, val title: Int, val text: Int)
        val pages = listOf(
            OnboardingPage(
                R.raw.device_shield_tracker_count,
                R.string.atp_OnboardingLastPageOneTitle, R.string.atp_OnboardingLatsPageOneSubtitle
            ),
            OnboardingPage(
                R.raw.device_shield_tracking_apps,
                R.string.atp_OnboardingLastPageTwoTitle, R.string.atp_OnboardingLastPageTwoSubTitle
            ),
            OnboardingPage(
                R.drawable.device_shield_onboarding_page_three_header,
                R.string.atp_OnboardingLastPageThreeTitle, R.string.atp_OnboardingLastPageThreeSubTitle
            )
        )
    }

}