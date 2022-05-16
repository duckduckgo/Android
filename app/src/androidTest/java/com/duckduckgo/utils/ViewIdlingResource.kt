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

package com.duckduckgo.utils

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.ViewFinder
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Matcher
import java.lang.Exception
import java.lang.reflect.Field

class ViewIdlingResource(
    private val viewMatcher: Matcher<View?>?,
    private val idleMatcher: Matcher<View?>?
) : IdlingResource {

    private var resourceCallback: IdlingResource.ResourceCallback? = null

    override fun isIdleNow(): Boolean {
        val view: View? = getView(viewMatcher)
        val isIdle: Boolean = idleMatcher?.matches(view) ?: false
        if (isIdle) {
            resourceCallback?.onTransitionToIdle()
        }
        return isIdle
    }

    override fun registerIdleTransitionCallback(resourceCallback: IdlingResource.ResourceCallback?) {
        this.resourceCallback = resourceCallback
    }

    override fun getName(): String? {
        return "$this ${viewMatcher.toString()}"
    }

    private fun getView(viewMatcher: Matcher<View?>?): View? {
        return try {
            val viewInteraction = onView(viewMatcher)
            val finderField: Field? = viewInteraction.javaClass.getDeclaredField("viewFinder")
            finderField?.isAccessible = true
            val finder = finderField?.get(viewInteraction) as ViewFinder
            finder.view
        } catch (e: Exception) {
            null
        }
    }
}

fun waitUntilViewIsDisplayed(matcher: Matcher<View?>) {
    val idlingResource: IdlingResource = ViewIdlingResource(matcher, isDisplayed())
    try {
        IdlingRegistry.getInstance().register(idlingResource)
        onView(withId(0)).check(doesNotExist())
    } finally {
        IdlingRegistry.getInstance().unregister(idlingResource)
    }
}

