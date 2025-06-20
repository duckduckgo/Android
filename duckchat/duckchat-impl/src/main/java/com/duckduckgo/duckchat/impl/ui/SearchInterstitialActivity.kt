/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.ui

import android.os.Bundle
import android.transition.ChangeBounds
import android.view.animation.OvershootInterpolator
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.navigation.api.GlobalActivityStarter

data class SearchInterstitialActivityParams(
    val query: String,
) : GlobalActivityStarter.ActivityParams

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(SearchInterstitialActivityParams::class)
class SearchInterstitialActivity : DuckDuckGoActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_interstitial)

        val transition = ChangeBounds().apply {
            duration = TRANSITION_DURATION
            interpolator = OvershootInterpolator(TRANSITION_INTERPOLATOR_TENSION)
        }

        window.apply {
            sharedElementEnterTransition = transition
            sharedElementReturnTransition = transition
        }
    }

    companion object {
        // TODO: This is in an :impl module and accessed directly from :app module, it should be moved to an API
        const val QUERY = "query"
        const val TRANSITION_DURATION = 300L
        const val TRANSITION_INTERPOLATOR_TENSION = 1F
    }
}
