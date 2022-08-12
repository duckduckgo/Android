package com.duckduckgo.actions

import androidx.annotation.IdRes
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.withId

fun enterTextFunction(@IdRes viewId: Int, text: String) {
    onView(withId(viewId))
        .perform(typeText(text), closeSoftKeyboard())
}

fun replaceTextFunction(@IdRes viewId: Int, text: String) {
    onView(withId(viewId))
        .perform(replaceText(text), closeSoftKeyboard())
}

fun scrollAndEnterTextFunction(@IdRes viewId: Int, text: String) {
    onView(withId(viewId))
        .perform(scrollTo(), typeText(text), closeSoftKeyboard())
}

fun deleteTextFunction(@IdRes viewId: Int) {
    onView(withId(viewId))
        .perform(clearText(), closeSoftKeyboard())
}