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

package com.duckduckgo.subscriptions.api

import android.content.Context
import android.view.View
import androidx.annotation.StringRes
import com.duckduckgo.common.utils.plugins.ActivePlugin

/**
 * A single step in the native subscription onboarding flow.
 *
 * Steps are contributed as plugins (from any module) so that each feature — VPN, Duck.ai, etc. —
 * can showcase itself. Steps are displayed in order of their plugin `priority`. The host screen is
 * intentionally thin: it owns only the toolbar title and hosts the plugin's view. The plugin owns
 * its entire layout (icon, headline, subtitle, any "Learn More" links, feature content and the
 * buttons) and decides when the step is finished by calling [SubscriptionOnboardingStepNavigator].
 */
interface SubscriptionOnboardingStepPlugin : ActivePlugin {

    /** Stable id used to re-resolve this step after fragment/process recreation. */
    val name: String

    /** Title shown in the host toolbar while this step is active. */
    @get:StringRes
    val toolbarTitle: Int

    /**
     * The plugin's entire view: header, feature content and buttons. The plugin owns this layout,
     * any link navigation and any module-specific button actions. It fills the whole host body and
     * calls [navigator] when the step is finished.
     */
    fun getOnboardingStepView(
        context: Context,
        navigator: SubscriptionOnboardingStepNavigator,
    ): View
}

/**
 * Passed by the host into each step's view so the step can advance the flow.
 */
fun interface SubscriptionOnboardingStepNavigator {
    /**
     * Call when this step is finished — after any module-specific action, synchronously or async.
     * Advances to the next ordered step, or closes the onboarding screen if this was the last one.
     */
    fun onStepComplete()
}
