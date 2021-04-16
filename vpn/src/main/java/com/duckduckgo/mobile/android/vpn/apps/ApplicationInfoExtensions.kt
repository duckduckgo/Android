/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.apps

import android.content.pm.ApplicationInfo
import android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED

private fun parseAppCategory(category: Int): AppCategory {
    return when (category) {
        ApplicationInfo.CATEGORY_AUDIO -> AppCategory.Audio
        ApplicationInfo.CATEGORY_VIDEO -> AppCategory.Video
        ApplicationInfo.CATEGORY_GAME -> AppCategory.Game
        ApplicationInfo.CATEGORY_IMAGE -> AppCategory.Image
        ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.Social
        ApplicationInfo.CATEGORY_NEWS -> AppCategory.News
        ApplicationInfo.CATEGORY_MAPS -> AppCategory.Maps
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.Productivity
        else -> AppCategory.Undefined
    }
}

fun ApplicationInfo.parseAppCategory(): AppCategory {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        parseAppCategory(category)
    } else {
        AppCategory.Undefined
    }
}

fun ApplicationInfo.shouldBeInExclusionList(): Boolean {
    return VpnExclusionList.isDdgApp(packageName) || VpnExclusionList.EXCLUDED_APPS.contains(packageName) || isGame()
}

fun ApplicationInfo.getAppCategoryCompat(): Int {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        category
    } else {
        CATEGORY_UNDEFINED
    }
}

fun ApplicationInfo.isGame(): Boolean {
    return getAppCategoryCompat() == ApplicationInfo.CATEGORY_GAME
}

fun ApplicationInfo.getAppType(): String? {
    return if ((flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
        "System"
    } else {
        null
    }
}
