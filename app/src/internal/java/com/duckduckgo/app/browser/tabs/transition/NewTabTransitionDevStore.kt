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
package com.duckduckgo.app.browser.tabs.transition

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

interface NewTabTransitionDevStore {
    var config: NewTabTransitionConfig
}

/** The dev settings sliders' ranges. The store keeps saved values within them too. */
object NewTabTransitionSliders {
    val START_SIZE_RANGE = 40..100
    val BELOW_CENTER_OFFSET_RANGE = -30..50
    val RIGHT_OF_CENTER_OFFSET_RANGE = 0..30
    val SCALE_BACK_RANGE = 75..100
    val DIM_RANGE = 0..100
    val DURATION_RANGE = 150..900
    const val DURATION_STEP = 10
    val SLIDE_LEFT_RANGE = 0..50
}

@ContributesBinding(AppScope::class)
class RealNewTabTransitionDevStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : NewTabTransitionDevStore {

    private val preferences: SharedPreferences by lazy { sharedPreferencesProvider.getSharedPreferences(FILENAME) }

    override var config: NewTabTransitionConfig
        get() {
            val default = NewTabTransitionConfig.DEFAULT
            return NewTabTransitionConfig(
                browserOrigin = readOrigin(KEY_BROWSER_ORIGIN, default.browserOrigin),
                tabManagerOrigin = readOrigin(KEY_TAB_MANAGER_ORIGIN, default.tabManagerOrigin),
                belowCenter = readProfile(NewTabOrigin.BELOW_CENTER, default.belowCenter),
                rightOfCenter = readProfile(NewTabOrigin.RIGHT_OF_CENTER, default.rightOfCenter),
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
            putBoolean(KEY_SLOW_MOTION, value.slowMotion)
        }

    private fun readOrigin(
        key: String,
        default: NewTabOrigin,
    ): NewTabOrigin = preferences.getString(key, null)
        ?.let { saved -> NewTabOrigin.entries.firstOrNull { it.name == saved } }
        ?: default

    // The origin pages' sliders reject a value outside their range or off their step, so what's read is kept within them.
    private fun readProfile(
        origin: NewTabOrigin,
        default: OriginProfile,
    ) = OriginProfile(
        startSizePercent = readInt(origin, KEY_START_SIZE, default.startSizePercent, NewTabTransitionSliders.START_SIZE_RANGE),
        offsetPercent = readInt(origin, KEY_OFFSET, default.offsetPercent, offsetRange(origin)),
        scaleBackPercent = readInt(origin, KEY_SCALE_BACK, default.scaleBackPercent, NewTabTransitionSliders.SCALE_BACK_RANGE),
        dimPercent = readInt(origin, KEY_DIM, default.dimPercent, NewTabTransitionSliders.DIM_RANGE),
        slideLeftPercent = readInt(origin, KEY_SLIDE_LEFT, default.slideLeftPercent, NewTabTransitionSliders.SLIDE_LEFT_RANGE),
        durationMs = readInt(origin, KEY_DURATION, default.durationMs, NewTabTransitionSliders.DURATION_RANGE).let {
            it - (it - NewTabTransitionSliders.DURATION_RANGE.first) % NewTabTransitionSliders.DURATION_STEP
        },
    )

    private fun readInt(
        origin: NewTabOrigin,
        field: String,
        default: Int,
        range: IntRange,
    ): Int = preferences.getInt(origin.key(field), default).coerceIn(range)

    private fun offsetRange(origin: NewTabOrigin): IntRange = when (origin) {
        NewTabOrigin.BELOW_CENTER -> NewTabTransitionSliders.BELOW_CENTER_OFFSET_RANGE
        NewTabOrigin.RIGHT_OF_CENTER -> NewTabTransitionSliders.RIGHT_OF_CENTER_OFFSET_RANGE
    }

    private fun NewTabOrigin.key(field: String) = "${field}_$name"

    companion object {
        const val FILENAME = "com.duckduckgo.app.browser.tabs.new_tab_transition"
        private const val KEY_BROWSER_ORIGIN = "KEY_BROWSER_ORIGIN"
        private const val KEY_TAB_MANAGER_ORIGIN = "KEY_TAB_MANAGER_ORIGIN"
        private const val KEY_START_SIZE = "KEY_START_SIZE"
        private const val KEY_OFFSET = "KEY_OFFSET"
        private const val KEY_SCALE_BACK = "KEY_SCALE_BACK"
        private const val KEY_DIM = "KEY_DIM"
        private const val KEY_DURATION = "KEY_DURATION"
        private const val KEY_SLIDE_LEFT = "KEY_SLIDE_LEFT"
        private const val KEY_SLOW_MOTION = "KEY_SLOW_MOTION"
    }
}
