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

package com.duckduckgo.duckchat.impl.inputscreen.ui

import android.os.Build.VERSION
import android.os.Bundle
import com.duckduckgo.anvil.annotations.ContributeToActivityStarter
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.navigation.api.GlobalActivityStarter

data class InputScreenActivityParams(
    val query: String,
) : GlobalActivityStarter.ActivityParams

@InjectWith(ActivityScope::class)
@ContributeToActivityStarter(InputScreenActivityParams::class)
class InputScreenActivity : DuckDuckGoActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_screen)
    }

    override fun finish() {
        super.finish()
        applyExitTransition()
    }

    private fun applyExitTransition() {
        if (VERSION.SDK_INT >= 34) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                CommonR.anim.slide_in_from_bottom_fade_in,
                CommonR.anim.slide_out_to_top_fade_out,
            )
        } else {
            overridePendingTransition(
                CommonR.anim.slide_in_from_bottom_fade_in,
                CommonR.anim.slide_out_to_top_fade_out,
            )
        }
    }

    companion object {
        // TODO: This is in an :impl module and accessed directly from :app module, it should be moved to an API
        const val QUERY = "query"
        const val TAB_ID = "tab_id"
    }
}
