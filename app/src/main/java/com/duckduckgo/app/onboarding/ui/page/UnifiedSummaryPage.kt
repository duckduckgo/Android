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

import android.content.Context
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacy.store.PrivacySettingsStore
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.content_onboarding_unified_browsing_protection.*
import kotlinx.android.synthetic.main.content_onboarding_unified_summary.*
import javax.inject.Inject


class UnifiedSummaryPage : OnboardingPageFragment() {

    @Inject
    lateinit var privacySettingsStore: PrivacySettingsStore

    override fun layoutResource(): Int = R.layout.content_onboarding_unified_summary

    override fun onAttach(context: Context) {
        super.onAttach(context)
        AndroidSupportInjection.inject(this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val titleText = extractTitleText()
        title.setText(titleText)

        extractContinueButtonTextResourceId()?.let { continueButton.setText(it) }
        continueButton.setOnClickListener { onContinuePressed() }

        updateUiToReflectTrackingState()
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser) {
            updateUiToReflectTrackingState()
        }
    }

    @StringRes
    private fun extractTitleText(): Int {
        return arguments?.getInt(TITLE_TEXT_RESOURCE_ID_EXTRA, R.string.unifiedOnboardingTitleFirstVisit)
            ?: R.string.unifiedOnboardingTitleFirstVisit
    }

    /**
     * Updates the UI elements to reflect whether tracker protection is currently on or off
     *
     * Note, this might be called before the usual fragment lifecycle has had a chance to run, so need to guard against that by null checking view.
     */
    private fun updateUiToReflectTrackingState() {
        if (view == null) return

        val resources = if (privacySettingsStore.privacyOn) {
            TrackerBlockingUiResources(R.drawable.icon_tracker_blocking_enabled, R.string.unifiedOnboardingBrowsingProtectionEnabledSubtitle)
        } else {
            TrackerBlockingUiResources(R.drawable.icon_tracker_blocking_disabled, R.string.unifiedOnboardingBrowsingProtectionDisabledSubtitle)
        }

        browsingProtectionIcon.setImageResource(resources.icon)
        browserProtectionSubtitle.setText(resources.browserProtectionSubtitle)
    }

    data class TrackerBlockingUiResources(@DrawableRes val icon: Int, @StringRes val browserProtectionSubtitle: Int)
}