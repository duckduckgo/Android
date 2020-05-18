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

package com.duckduckgo.app.feedback.ui.negative

import java.io.Serializable

sealed class FeedbackType {

    enum class MainReason {
        MISSING_BROWSING_FEATURES,
        WEBSITES_NOT_LOADING,
        SEARCH_NOT_GOOD_ENOUGH,
        NOT_ENOUGH_CUSTOMIZATIONS,
        APP_IS_SLOW_OR_BUGGY,
        OTHER
    }

    interface SubReason : Serializable

    enum class MissingBrowserFeaturesSubReasons : SubReason {
        NAVIGATION_ISSUES,
        TAB_MANAGEMENT,
        AD_POPUP_BLOCKING,
        WATCHING_VIDEOS,
        INTERACTING_IMAGES,
        BOOKMARK_MANAGEMENT,
        OTHER
    }

    enum class SearchNotGoodEnoughSubReasons : SubReason {
        PROGRAMMING_TECHNICAL_SEARCHES,
        LAYOUT_MORE_LIKE_GOOGLE,
        FASTER_LOAD_TIME,
        SEARCHING_IN_SPECIFIC_LANGUAGE,
        BETTER_AUTOCOMPLETE,
        OTHER
    }

    enum class CustomizationSubReasons : SubReason {
        HOME_SCREEN_CONFIGURATION,
        TAB_DISPLAY,
        HOW_APP_LOOKS,
        WHICH_DATA_IS_CLEARED,
        WHEN_DATA_IS_CLEARED,
        BOOKMARK_DISPLAY,
        OTHER
    }

    enum class PerformanceSubReasons : SubReason {
        SLOW_WEB_PAGE_LOADS,
        APP_CRASHES_OR_FREEZES,
        MEDIA_PLAYBACK_BUGS,
        OTHER
    }
}
