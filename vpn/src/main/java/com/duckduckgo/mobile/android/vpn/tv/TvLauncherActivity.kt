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

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.preference.PreferenceManager
import com.duckduckgo.mobile.android.vpn.R

class TvLauncherActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setBackgroundDrawable(
            ColorDrawable(resources.getColor(com.duckduckgo.mobile.android.R.color.marketing_red)))

        PreferenceManager.getDefaultSharedPreferences(this).apply {
            if (!getBoolean(COMPLETED_ONBOARDING_PREF_KEY, false)) {
                val fragment: GuidedStepSupportFragment = AppTPDialogFragment()
                GuidedStepSupportFragment.addAsRoot(
                    this@TvLauncherActivity, fragment, android.R.id.content)
                //                startActivity(Intent(this@TvLauncherActivity,
                // TVOnboardingActivity::class.java))
            } else {
                startActivity(TvTrackerDetailsActivity.intent(this@TvLauncherActivity))
            }
        }
    }

    companion object {
        const val COMPLETED_ONBOARDING_PREF_KEY = "COMPLETED_ONBOARDING_PREF_KEY"
    }
}
