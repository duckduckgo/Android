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

package com.duckduckgo.app.browser.tabs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

/** Set from Developer Settings in internal builds; other builds never enable the override. */
interface NewTabTransitionSettings {
    var isOverrideEnabled: Boolean
    var transition: NewTabTransition
    var tabManagerCloseBehaviour: TabManagerCloseBehaviour
    var animateClosingTabs: Boolean

    /** Reads SharedPreferences, so call it off the main thread. Null when the override is off. */
    fun enabledTransition(): NewTabTransition?

    /** The transition to play backwards when the current tab closes. Reads SharedPreferences; null when off. */
    fun enabledCloseTransition(): NewTabTransition?

    /** Whether the tab manager skips its close animation when it opens a new tab. Reads SharedPreferences. */
    fun hidesTabManagerImmediately(): Boolean
}

enum class TabManagerCloseBehaviour {
    SLIDE,
    HIDE_IMMEDIATELY,
}

@ContributesBinding(AppScope::class)
class NewTabTransitionSharedPreferences @Inject constructor(private val context: Context) : NewTabTransitionSettings {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    override var isOverrideEnabled: Boolean
        get() = preferences.getBoolean(KEY_OVERRIDE_ENABLED, false)
        set(enabled) = preferences.edit { putBoolean(KEY_OVERRIDE_ENABLED, enabled) }

    override var transition: NewTabTransition
        get() = preferences.getString(KEY_TRANSITION, null)
            ?.let { saved -> NewTabTransition.entries.firstOrNull { it.name == saved } }
            ?: NewTabTransition.SHARED_AXIS
        set(value) = preferences.edit { putString(KEY_TRANSITION, value.name) }

    override var tabManagerCloseBehaviour: TabManagerCloseBehaviour
        get() = preferences.getString(KEY_TAB_MANAGER_CLOSE_BEHAVIOUR, null)
            ?.let { saved -> TabManagerCloseBehaviour.entries.firstOrNull { it.name == saved } }
            ?: TabManagerCloseBehaviour.SLIDE
        set(value) = preferences.edit { putString(KEY_TAB_MANAGER_CLOSE_BEHAVIOUR, value.name) }

    override var animateClosingTabs: Boolean
        get() = preferences.getBoolean(KEY_ANIMATE_CLOSING_TABS, true)
        set(enabled) = preferences.edit { putBoolean(KEY_ANIMATE_CLOSING_TABS, enabled) }

    override fun enabledTransition(): NewTabTransition? = if (isOverrideEnabled) transition else null

    override fun enabledCloseTransition(): NewTabTransition? = if (isOverrideEnabled && animateClosingTabs) transition else null

    override fun hidesTabManagerImmediately(): Boolean =
        isOverrideEnabled && tabManagerCloseBehaviour == TabManagerCloseBehaviour.HIDE_IMMEDIATELY

    private companion object {
        const val FILENAME = "com.duckduckgo.app.browser.tabs.new_tab_transition"
        const val KEY_OVERRIDE_ENABLED = "KEY_OVERRIDE_ENABLED"
        const val KEY_TRANSITION = "KEY_TRANSITION"
        const val KEY_TAB_MANAGER_CLOSE_BEHAVIOUR = "KEY_TAB_MANAGER_CLOSE_BEHAVIOUR"
        const val KEY_ANIMATE_CLOSING_TABS = "KEY_ANIMATE_CLOSING_TABS"
    }
}
