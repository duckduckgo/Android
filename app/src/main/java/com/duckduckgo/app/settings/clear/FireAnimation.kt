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

import com.duckduckgo.app.browser.R
import com.duckduckgo.app.settings.clear.FireAnimation.HeroAbstract
import com.duckduckgo.app.settings.clear.FireAnimation.HeroFire
import com.duckduckgo.app.settings.clear.FireAnimation.HeroWater
import com.duckduckgo.app.settings.clear.FireAnimation.None
import com.duckduckgo.app.statistics.pixels.Pixel
import java.io.Serializable

sealed class FireAnimation(
    val resId: Int,
    val nameResId: Int,
) : Serializable {
    data object HeroFire : FireAnimation(R.raw.hero_fire_inferno, R.string.settingsHeroFireAnimation)
    data object HeroWater : FireAnimation(R.raw.hero_water_whirlpool, R.string.settingsHeroWaterAnimation)
    data object HeroAbstract : FireAnimation(R.raw.hero_abstract_airstream, R.string.settingsHeroAbstractAnimation)
    data object None : FireAnimation(-1, R.string.settingsNoneAnimation)

    fun getOptionIndex(): Int {
        return when (this) {
            HeroFire -> 1
            HeroWater -> 2
            HeroAbstract -> 3
            None -> 4
        }
    }

    fun Int.getAnimationForIndex(): FireAnimation {
        return when (this) {
            2 -> HeroWater
            3 -> HeroAbstract
            4 -> None
            else -> HeroFire
        }
    }
}

fun FireAnimation.getPixelValue() = when (this) {
    HeroFire -> Pixel.PixelValues.FIRE_ANIMATION_INFERNO
    HeroWater -> Pixel.PixelValues.FIRE_ANIMATION_WHIRLPOOL
    HeroAbstract -> Pixel.PixelValues.FIRE_ANIMATION_AIRSTREAM
    None -> Pixel.PixelValues.FIRE_ANIMATION_NONE
}
