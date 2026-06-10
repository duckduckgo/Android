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

package com.duckduckgo.app.browser

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.duckduckgo.espresso.waitForView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import com.google.android.material.R as MaterialR

/**
 * Waits for a menu item to be visible in the popup menu or bottom sheet,
 * then clicks it. Handles both menu types transparently so callers don't
 * need to worry about which root the view is in.
 *
 * @param viewMatcher The matcher for the menu item to wait for and click.
 */
fun clickMenuItem(viewMatcher: Matcher<View>) {
    try {
        onView(isRoot()).perform(waitForView(viewMatcher, timeout = 2000))
        onView(viewMatcher).perform(click())
    } catch (_: PerformException) {
        // Bottom sheet path: expand fully so the NestedScrollView has room, then scroll and click.
        onView(withId(MaterialR.id.design_bottom_sheet))
            .inRoot(isDialog())
            .perform(expandBottomSheet())
        onView(viewMatcher).inRoot(isDialog()).perform(scrollTo(), click())
    }
}

private fun expandBottomSheet(): ViewAction {
    return object : ViewAction {
        override fun getConstraints(): Matcher<View> = Matchers.any(View::class.java)
        override fun getDescription(): String = "expand bottom sheet to full height"
        override fun perform(uiController: UiController, view: View) {
            val behavior = BottomSheetBehavior.from(view)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            // Wait for the expansion animation to complete
            uiController.loopMainThreadForAtLeast(500)
        }
    }
}
