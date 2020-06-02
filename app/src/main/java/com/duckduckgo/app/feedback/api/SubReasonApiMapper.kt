/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.feedback.api

import com.duckduckgo.app.feedback.ui.negative.FeedbackType.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.CustomizationSubReasons.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MissingBrowserFeaturesSubReasons.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.PerformanceSubReasons.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.SearchNotGoodEnoughSubReasons.*
import javax.inject.Inject

class SubReasonApiMapper @Inject constructor() {

    fun apiKeyFromSubReason(subReason: SubReason?): String {
        return when (subReason) {
            is MissingBrowserFeaturesSubReasons -> browserFeaturesSubReason(subReason)
            is SearchNotGoodEnoughSubReasons -> searchNotGoodEnoughSubReason(subReason)
            is CustomizationSubReasons -> customizationSubReason(subReason)
            is PerformanceSubReasons -> performanceSubReasons(subReason)
            else -> GENERIC
        }
    }

    private fun browserFeaturesSubReason(subReason: MissingBrowserFeaturesSubReasons): String {
        return when (subReason) {
            NAVIGATION_ISSUES -> "navigation"
            TAB_MANAGEMENT -> "tabs"
            AD_POPUP_BLOCKING -> "ads"
            WATCHING_VIDEOS -> "videos"
            INTERACTING_IMAGES -> "images"
            BOOKMARK_MANAGEMENT -> "bookmarks"
            MissingBrowserFeaturesSubReasons.OTHER -> OTHER
        }
    }

    private fun searchNotGoodEnoughSubReason(subReason: SearchNotGoodEnoughSubReasons): String {
        return when (subReason) {
            PROGRAMMING_TECHNICAL_SEARCHES -> "technical"
            LAYOUT_MORE_LIKE_GOOGLE -> "layout"
            FASTER_LOAD_TIME -> "speed"
            SEARCHING_IN_SPECIFIC_LANGUAGE -> "langRegion"
            BETTER_AUTOCOMPLETE -> "autocomplete"
            SearchNotGoodEnoughSubReasons.OTHER -> OTHER
        }
    }

    private fun customizationSubReason(subReason: CustomizationSubReasons): String {
        return when (subReason) {
            HOME_SCREEN_CONFIGURATION -> "home"
            TAB_DISPLAY -> "tabs"
            HOW_APP_LOOKS -> "ui"
            WHICH_DATA_IS_CLEARED -> "whichDataCleared"
            WHEN_DATA_IS_CLEARED -> "whenDataCleared"
            BOOKMARK_DISPLAY -> "bookmarks"
            CustomizationSubReasons.OTHER -> OTHER
        }
    }

    private fun performanceSubReasons(subReason: PerformanceSubReasons): String {
        return when (subReason) {
            SLOW_WEB_PAGE_LOADS -> "slow"
            APP_CRASHES_OR_FREEZES -> "crash"
            MEDIA_PLAYBACK_BUGS -> "video"
            PerformanceSubReasons.OTHER -> OTHER
        }
    }

    companion object {
        private const val OTHER = "other"
        private const val GENERIC = "submit"
    }

}
