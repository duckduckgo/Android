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

import android.os.Build
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import androidx.test.filters.SdkSuppress
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.onboarding.ui.OnboardingActivity
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaxDialogsJourneyTest {

    /**
     * Use [ActivityScenarioRule] to create and launch the activity under test before each test,
     * and close it after each test. This is a replacement for
     * [androidx.test.rule.ActivityTestRule].
     */
    @get:Rule
    var activityScenarioRule = activityScenarioRule<OnboardingActivity>()

    @Test
    @UserJourney
    @FlakyTest
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O, maxSdkVersion = Build.VERSION_CODES.P)
    fun daxDialogs_supports_default_browser_journey() {
        onView(isRoot()).perform(waitForView(withId(R.id.primaryCta)))
        onView(withId(R.id.primaryCta)).perform(click())

        onView(isRoot()).perform(waitForView(withId(R.id.continueButton)))
        onView(withId(R.id.continueButton)).perform(click())

        onView(isRoot()).perform(waitForView(withId(R.id.browserMenu)))
        onView(allOf(withId(R.id.browserMenu), isClickable())).perform(click())

        onView(withId(R.id.forwardMenuItem)).check(matches(isDisplayed()))
    }
}
