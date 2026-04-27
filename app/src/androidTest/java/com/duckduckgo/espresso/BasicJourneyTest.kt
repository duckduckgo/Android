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

package com.duckduckgo.espresso

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isClickable
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.clickMenuItem
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.duckduckgo.browser.ui.R as BrowserUiR

@RunWith(AndroidJUnit4::class)
class BasicJourneyTest {

    @get:Rule
    var activityScenarioRule = activityScenarioRule<BrowserActivity>()

    @Test @UserJourney
    fun browser_openPopUp() {
        // dismiss any first-run dialogs (e.g., widget promo)
        dismissBlockingDialogs()

        // since we use a fake toolbar, we want to wait until the real one is visible
        onView(isRoot()).perform(waitForView(withId(R.id.browserMenu)))

        // tap on menu
        onView(allOf(withId(R.id.browserMenu), isClickable())).perform(click())

        // check that the forward button is visible
        clickMenuItem(withId(BrowserUiR.id.forwardMenuItem))
    }

    private fun dismissBlockingDialogs() {
        // dismiss the home screen widget promo if present
        runCatching {
            onView(isRoot()).inRoot(isDialog())
                .perform(waitForView(withId(R.id.homeScreenWidgetBottomSheetDialogGhostButton), timeout = 3000))
            onView(withId(R.id.homeScreenWidgetBottomSheetDialogGhostButton)).perform(click())
        }
    }
}
