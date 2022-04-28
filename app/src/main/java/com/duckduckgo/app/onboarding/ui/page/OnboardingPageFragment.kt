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

package com.duckduckgo.app.onboarding.ui.page

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.LayoutRes
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import com.duckduckgo.app.onboarding.ui.OnboardingActivity
import kotlinx.android.synthetic.main.content_onboarding_default_browser.*

abstract class OnboardingPageFragment : Fragment() {

    @LayoutRes
    abstract fun layoutResource(): Int

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? =
        inflater.inflate(layoutResource(), container, false)

    fun onContinuePressed() {
        when (activity) {
            is OnboardingActivity -> (activity as OnboardingActivity).onContinueClicked()
        }
    }

    fun onOnboardingDone() {
        when (activity) {
            is OnboardingActivity -> (activity as OnboardingActivity).onOnboardingDone()
        }
    }

    internal fun applyStyle() {
        activity?.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            statusBarColor = Color.WHITE
        }
        ViewCompat.requestApplyInsets(longDescriptionContainer)
    }

}
