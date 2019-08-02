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
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.privacy.store.PrivacySettingsStore
import dagger.android.support.AndroidSupportInjection
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

        extractContinueButtonTextResourceId()?.let { continueButton.setText(it) }
        continueButton.setOnClickListener { onContinuePressed() }
    }
}