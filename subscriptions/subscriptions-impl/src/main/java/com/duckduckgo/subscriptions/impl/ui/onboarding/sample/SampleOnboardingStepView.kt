/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.ui.onboarding.sample

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.subscriptions.api.SubscriptionOnboardingStepNavigator
import com.duckduckgo.subscriptions.api.SubscriptionScreens.SubscriptionsSettingsScreenWithEmptyParams
import com.duckduckgo.subscriptions.impl.databinding.ViewSampleOnboardingStepBinding
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

/**
 * Placeholder content view for [SampleOnboardingStepPlugin]. Declares its own header, content and
 * primary button, navigates a "Learn More" link to a native screen, and finishes the step via the
 * injected [navigator].
 */
@InjectWith(ViewScope::class)
class SampleOnboardingStepView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle) {

    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    var navigator: SubscriptionOnboardingStepNavigator? = null

    private val binding: ViewSampleOnboardingStepBinding by viewBinding()

    init {
        orientation = VERTICAL
    }

    override fun onAttachedToWindow() {
        AndroidSupportInjection.inject(this)
        super.onAttachedToWindow()

        binding.learnMore.setOnClickListener {
            globalActivityStarter.start(context, SubscriptionsSettingsScreenWithEmptyParams)
        }
        binding.primaryButton.setOnClickListener {
            navigator?.onStepComplete()
        }
    }
}
