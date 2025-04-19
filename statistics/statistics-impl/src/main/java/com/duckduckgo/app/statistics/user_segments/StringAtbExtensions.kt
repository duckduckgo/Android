/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.statistics.user_segments

internal fun String.parseAtbWeek(): Int {
    val startIndex = this.indexOf('v') + 1
    val endIndex = this.indexOf('-', startIndex)
    return if (endIndex > 0 && startIndex < endIndex) {
        this.substring(startIndex, endIndex).toInt()
    } else {
        this.substringAfter('v', "").toInt()
    }
}

internal fun String.asNumber(): Int {
    fun String.parseDay(): Int? {
        val day = this.substringAfterLast('-', "").firstOrNull()
        return day?.digitToIntOrNull()
    }
    val week = this.parseAtbWeek()
    val day = this.parseDay() ?: 0

    return week * 7 + day - 1
}
