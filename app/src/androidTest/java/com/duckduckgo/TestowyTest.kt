/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo

import android.view.View
import android.view.ViewGroup
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import androidx.test.runner.AndroidJUnit4
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.launch.LaunchBridgeActivity
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class TestowyTest {

    @Rule
    @JvmField
    var mActivityTestRule = ActivityTestRule(LaunchBridgeActivity::class.java)

    @Test
    fun testowyTest() {
        val materialButton = onView(
            allOf(
                withId(R.id.primaryCta), withText("Let's Do It!"),
                childAtPosition(
                    allOf(
                        withId(R.id.daxCtaContainer),
                        childAtPosition(
                            withId(R.id.longDescriptionContainer),
                            2
                        )
                    ),
                    3
                ),
                isDisplayed()
            )
        )
        materialButton.perform(click())

        val frameLayout = onView(
            allOf(
                withId(R.id.browserMenu),
                childAtPosition(
                    allOf(
                        withId(R.id.toolbarContainer),
                        childAtPosition(
                            withId(R.id.appBarLayout),
                            0
                        )
                    ),
                    4
                ),
                isDisplayed()
            )
        )
        frameLayout.perform(click())
    }

    private fun childAtPosition(
        parentMatcher: Matcher<View>,
        position: Int
    ): Matcher<View> {

        return object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("Child at position $position in parent ")
                parentMatcher.describeTo(description)
            }

            public override fun matchesSafely(view: View): Boolean {
                val parent = view.parent
                return parent is ViewGroup && parentMatcher.matches(parent)
                    && view == parent.getChildAt(position)
            }
        }
    }
}
