/*
 * Copyright (c) 2023 DuckDuckGo
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

import android.view.*
import androidx.test.espresso.*
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.duckduckgo.app.browser.R
import org.hamcrest.*

// used to introduce a delay not blocking main thread
fun waitFor(delay: Long): ViewAction {
    return object : ViewAction {
        override fun getConstraints(): Matcher<View> = ViewMatchers.isRoot()
        override fun getDescription(): String = "wait for $delay milliseconds"
        override fun perform(uiController: UiController, v: View?) {
            uiController.loopMainThreadForAtLeast(delay)
        }
    }
}

// The Home screen widget promo bottom sheet (CtaViewModel.canShowWidgetCta) can appear on top of
// BrowserActivity on fresh installs and obscure the activity's own root, breaking unrelated tests.
fun dismissWidgetPromoIfPresent() {
    runCatching {
        onView(isRoot()).inRoot(isDialog())
            .perform(waitForView(withId(R.id.homeScreenWidgetBottomSheetDialogGhostButton), timeout = 3000))
        onView(withId(R.id.homeScreenWidgetBottomSheetDialogGhostButton)).perform(click())
    }
}
