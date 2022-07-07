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

package com.duckduckgo.actions

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.CoreMatchers

fun tapOnButton(@IdRes viewId: Int) {
    onView(withId(viewId)).perform(click())
}

fun scrollAndTapButton(@IdRes viewId: Int) {
    onView(withId(viewId)).perform(scrollTo(), click())
}

fun tapOnTheView(@IdRes viewId: Int, @StringRes stringId: String) {
    onView(CoreMatchers.allOf(withId(viewId), ViewMatchers.withText(stringId)))
        .perform(click())
}

fun tapOnSpecificText(@IdRes viewId: Int, @StringRes stringId: Int) {
    onView(CoreMatchers.allOf(withId(viewId), ViewMatchers.withText(stringId)))
        .perform(scrollTo(), click())
}

