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
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

/** Set from Developer Settings in internal builds; other builds never enable it. */
interface NewTabTransitionSettings {
    var isEnabled: Boolean
    var config: NewTabTransitionConfig

    /** Reads SharedPreferences, so call it off the main thread. Null when the transitions are off. */
    fun enabledConfig(): NewTabTransitionConfig?

    /** Records that the tab manager is about to open a new tab, so it grows from the tab manager origin. */
    fun markNewTabFromTabManager()

    /** True once, shortly after [markNewTabFromTabManager]. */
    fun consumeNewTabFromTabManager(): Boolean

    /** The tab manager as it looked when it opened a new tab, for the browser to animate out. Cleared by the next mark. */
    fun putTabManagerSnapshot(snapshot: Bitmap)

    fun consumeTabManagerSnapshot(): Bitmap?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class NewTabTransitionSharedPreferences @Inject constructor(private val context: Context) : NewTabTransitionSettings {

    private val preferences: SharedPreferences by lazy { context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE) }

    @Volatile private var newTabFromTabManagerAt: Long? = null

    @Volatile private var tabManagerSnapshot: Bitmap? = null

    override var isEnabled: Boolean
        get() = preferences.getBoolean(KEY_ENABLED, false)
        set(enabled) = preferences.edit { putBoolean(KEY_ENABLED, enabled) }

    override var config: NewTabTransitionConfig
        get() {
            val default = NewTabTransitionConfig.DEFAULT
            return NewTabTransitionConfig(
                browserOrigin = readOrigin(KEY_BROWSER_ORIGIN, default.browserOrigin),
                tabManagerOrigin = readOrigin(KEY_TAB_MANAGER_ORIGIN, default.tabManagerOrigin),
                belowCenter = readProfile(NewTabOrigin.BELOW_CENTER, default.belowCenter),
                rightOfCenter = readProfile(NewTabOrigin.RIGHT_OF_CENTER, default.rightOfCenter),
                animateClose = preferences.getBoolean(KEY_ANIMATE_CLOSE, default.animateClose),
                animateTabManagerOut = preferences.getBoolean(KEY_ANIMATE_TAB_MANAGER_OUT, default.animateTabManagerOut),
                slowMotion = preferences.getBoolean(KEY_SLOW_MOTION, default.slowMotion),
            )
        }
        set(value) = preferences.edit {
            putString(KEY_BROWSER_ORIGIN, value.browserOrigin.name)
            putString(KEY_TAB_MANAGER_ORIGIN, value.tabManagerOrigin.name)
            NewTabOrigin.entries.forEach { origin ->
                val profile = value.profile(origin)
                putInt(origin.key(KEY_START_SIZE), profile.startSizePercent)
                putInt(origin.key(KEY_OFFSET), profile.offsetPercent)
                putInt(origin.key(KEY_SCALE_BACK), profile.scaleBackPercent)
                putInt(origin.key(KEY_DIM), profile.dimPercent)
                putInt(origin.key(KEY_SLIDE_LEFT), profile.slideLeftPercent)
                putInt(origin.key(KEY_DURATION), profile.durationMs)
            }
            putBoolean(KEY_ANIMATE_CLOSE, value.animateClose)
            putBoolean(KEY_ANIMATE_TAB_MANAGER_OUT, value.animateTabManagerOut)
            putBoolean(KEY_SLOW_MOTION, value.slowMotion)
        }

    override fun enabledConfig(): NewTabTransitionConfig? = if (isEnabled) config else null

    override fun markNewTabFromTabManager() {
        newTabFromTabManagerAt = SystemClock.uptimeMillis()
        tabManagerSnapshot = null
    }

    override fun consumeNewTabFromTabManager(): Boolean {
        val markedAt = newTabFromTabManagerAt ?: return false
        newTabFromTabManagerAt = null
        return SystemClock.uptimeMillis() - markedAt < TAB_MANAGER_MARK_TTL_MS
    }

    override fun putTabManagerSnapshot(snapshot: Bitmap) {
        tabManagerSnapshot = snapshot
    }

    override fun consumeTabManagerSnapshot(): Bitmap? = tabManagerSnapshot.also { tabManagerSnapshot = null }

    private fun readOrigin(
        key: String,
        default: NewTabOrigin,
    ): NewTabOrigin = preferences.getString(key, null)
        ?.let { saved -> NewTabOrigin.entries.firstOrNull { it.name == saved } }
        ?: default

    private fun readProfile(
        origin: NewTabOrigin,
        default: OriginProfile,
    ) = OriginProfile(
        startSizePercent = preferences.getInt(origin.key(KEY_START_SIZE), default.startSizePercent),
        offsetPercent = preferences.getInt(origin.key(KEY_OFFSET), default.offsetPercent),
        scaleBackPercent = preferences.getInt(origin.key(KEY_SCALE_BACK), default.scaleBackPercent),
        dimPercent = preferences.getInt(origin.key(KEY_DIM), default.dimPercent),
        slideLeftPercent = preferences.getInt(origin.key(KEY_SLIDE_LEFT), default.slideLeftPercent),
        durationMs = preferences.getInt(origin.key(KEY_DURATION), default.durationMs),
    )

    private fun NewTabOrigin.key(field: String) = "${field}_$name"

    private companion object {
        const val FILENAME = "com.duckduckgo.app.browser.tabs.new_tab_transition"
        const val KEY_ENABLED = "KEY_OVERRIDE_ENABLED"
        const val KEY_BROWSER_ORIGIN = "KEY_BROWSER_ORIGIN"
        const val KEY_TAB_MANAGER_ORIGIN = "KEY_TAB_MANAGER_ORIGIN"
        const val KEY_START_SIZE = "KEY_START_SIZE"
        const val KEY_OFFSET = "KEY_OFFSET"
        const val KEY_SCALE_BACK = "KEY_SCALE_BACK"
        const val KEY_DIM = "KEY_DIM"
        const val KEY_DURATION = "KEY_DURATION"
        const val KEY_SLIDE_LEFT = "KEY_SLIDE_LEFT"
        const val KEY_ANIMATE_CLOSE = "KEY_ANIMATE_CLOSE"
        const val KEY_ANIMATE_TAB_MANAGER_OUT = "KEY_ANIMATE_TAB_MANAGER_OUT"
        const val KEY_SLOW_MOTION = "KEY_SLOW_MOTION"
        const val TAB_MANAGER_MARK_TTL_MS = 2_000L
    }
}
