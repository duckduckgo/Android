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

package com.duckduckgo.app.browser.newaddressbaroption

import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Reports what the address bar toggle migration did, so its rollout can be judged and stopped. */
interface DuckAiToggleMigrationPixels {
    /** The migration turned Search & Duck.ai on for this user. */
    fun reportFlipped()

    /** A migrated user undid the migration, [millisSinceFlip] after it turned Search & Duck.ai on. */
    fun reportReverted(millisSinceFlip: Long, how: MigrationRevert)
}

/** How a migrated user undid the migration. Only user settings count, never a remote flag. */
enum class MigrationRevert(val paramValue: String) {
    /** Turned the Search & Duck.ai address bar setting off. */
    TOGGLE_OFF("toggle_off"),

    /** Turned Duck.ai off entirely, leaving the address bar setting on. */
    DUCKAI_OFF("duckai_off"),
}

@ContributesBinding(AppScope::class)
class RealDuckAiToggleMigrationPixels @Inject constructor(
    private val pixel: Pixel,
) : DuckAiToggleMigrationPixels {

    override fun reportFlipped() {
        pixel.fire(AppPixelName.DUCK_AI_TOGGLE_MIGRATION_FLIPPED_COUNT)
        pixel.fire(AppPixelName.DUCK_AI_TOGGLE_MIGRATION_FLIPPED_DAILY, type = Pixel.PixelType.Daily())
    }

    override fun reportReverted(millisSinceFlip: Long, how: MigrationRevert) {
        pixel.fire(
            AppPixelName.DUCK_AI_TOGGLE_MIGRATION_REVERTED,
            parameters = mapOf(
                PARAM_DAYS_SINCE_FLIP to daysSinceFlipBucket(millisSinceFlip),
                PARAM_HOW to how.paramValue,
            ),
        )
    }

    private fun daysSinceFlipBucket(millisSinceFlip: Long): String = when {
        millisSinceFlip <= ONE_DAY_MILLIS -> "le_1d"
        millisSinceFlip <= 7 * ONE_DAY_MILLIS -> "le_7d"
        millisSinceFlip <= 30 * ONE_DAY_MILLIS -> "le_30d"
        else -> "gt_30d"
    }

    private companion object {
        const val PARAM_DAYS_SINCE_FLIP = "days_since_flip"
        const val PARAM_HOW = "how"
        val ONE_DAY_MILLIS = TimeUnit.DAYS.toMillis(1)
    }
}
