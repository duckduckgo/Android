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
import java.io.Serializable

sealed class FireAnimation(val resId: Int, val nameResId: Int) : Serializable {
    object HeroFire : FireAnimation(R.raw.hero_fire_inferno, R.string.settingsHeroFireAnimation)
    object HeroWater : FireAnimation(R.raw.hero_water_whirlpool, R.string.settingsHeroWaterAnimation)
    object HeroAbstract : FireAnimation(R.raw.hero_abstract_airstream, R.string.settingsHeroAbstractAnimation)
    object Nonen : FireAnimation(-1, R.string.settingsNoneAnimation)
}
