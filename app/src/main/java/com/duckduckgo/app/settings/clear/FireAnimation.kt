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

enum class FireAnimation(val resId: Int) {
    HERO_FIRE_RISING(R.raw.fire_hero_rising),
    HERO_WATER_SWIRL(R.raw.water_swirl),
    HERO_ABSTRACT_SQUEEGEE(R.raw.abstract_hero_squeegee),
    DISABLED(-1)
}
