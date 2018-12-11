/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.settings.clear

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.statistics.pixels.Pixel
import java.util.concurrent.TimeUnit

enum class ClearWhenOption constructor(
    @IdRes val radioButtonId: Int,
    @StringRes val nameStringRes: Int,
    val durationMillis: Long,
    val pixelEvent: Pixel.PixelName?
) {
    APP_EXIT_ONLY(
        R.id.settingAppExitOnly,
        R.string.settingsAutomaticallyClearWhenAppExitOnly,
        0,
        Pixel.PixelName.AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_ONLY
    ),
    APP_EXIT_OR_5_MINS(
        R.id.settingInactive5Mins,
        R.string.settingsAutomaticallyClearWhenAppExit5Minutes,
        TimeUnit.MINUTES.toMillis(5),
        Pixel.PixelName.AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_5_MINS
    ),
    APP_EXIT_OR_15_MINS(
        R.id.settingInactive15Mins,
        R.string.settingsAutomaticallyClearWhenAppExit15Minutes,
        TimeUnit.MINUTES.toMillis(15),
        Pixel.PixelName.AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_15_MINS
    ),
    APP_EXIT_OR_30_MINS(
        R.id.settingInactive30Mins,
        R.string.settingsAutomaticallyClearWhenAppExit30Minutes,
        TimeUnit.MINUTES.toMillis(30),
        Pixel.PixelName.AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_30_MINS
    ),
    APP_EXIT_OR_60_MINS(
        R.id.settingInactive60Mins,
        R.string.settingsAutomaticallyClearWhenAppExit60Minutes,
        TimeUnit.MINUTES.toMillis(60),
        Pixel.PixelName.AUTOMATIC_CLEAR_DATA_WHEN_OPTION_APP_EXIT_OR_60_MINS
    ),

    // only available to debug builds
    APP_EXIT_OR_5_SECONDS(
        R.id.settingInactive5Seconds,
        R.string.settingsAutomaticallyClearWhenAppExit5Seconds,
        TimeUnit.SECONDS.toMillis(5),
        null
    ),
}