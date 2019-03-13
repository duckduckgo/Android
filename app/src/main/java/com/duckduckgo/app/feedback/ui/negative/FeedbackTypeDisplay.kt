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

import androidx.annotation.StringRes
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MainReason.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MainReason.OTHER
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MissingBrowserFeaturesSubReasons.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.SearchNotGoodEnoughSubReasons.*

fun MainReason.displayText(): FeedbackTypeDisplay.FeedbackTypeMainReasonDisplay? = FeedbackTypeDisplay.mainReasons[this]
fun SubReason.displayText(): FeedbackTypeDisplay.FeedbackTypeSubReasonDisplay? = FeedbackTypeDisplay.subReasons[this]

class FeedbackTypeDisplay {

    data class FeedbackTypeMainReasonDisplay(
        val mainReason: MainReason,

        @StringRes
        val listDisplayResId: Int,

        @StringRes
        val titleDisplayResId: Int,

        @StringRes
        val subtitleDisplayResId: Int
    )

    data class FeedbackTypeSubReasonDisplay(
        val subReason: SubReason,

        @StringRes
        val titleDisplayResId: Int
    )

    companion object {
        val mainReasons: Map<MainReason, FeedbackTypeMainReasonDisplay> = mutableMapOf<MainReason, FeedbackTypeMainReasonDisplay>().also {

            MISSING_BROWSING_FEATURES.also { type ->
                it[type] = FeedbackTypeMainReasonDisplay(
                    type,
                    listDisplayResId = R.string.missingBrowserFeaturesTitleLong,
                    titleDisplayResId = R.string.missingBrowserFeaturesTitleShort,
                    subtitleDisplayResId = R.string.missingBrowserFeaturesSubtitle
                )
            }

            WEBSITES_NOT_LOADING.also { type ->
                it[type] = FeedbackTypeMainReasonDisplay(
                    type,
                    R.string.websiteNotLoadingTitleShort,
                    R.string.websiteNotLoadingTitleLong,
                    R.string.websiteNotLoadingSubtitle
                )
            }

            SEARCH_NOT_GOOD_ENOUGH.also { type ->
                it[type] = FeedbackTypeMainReasonDisplay(
                    type,
                    R.string.searchNotGoodEnoughTitleLong,
                    R.string.searchNotGoodEnoughTitleShort,
                    R.string.searchNotGoodEnoughSubtitle
                )
            }

            NOT_ENOUGH_CUSTOMIZATIONS.also { type ->
                it[type] = FeedbackTypeMainReasonDisplay(
                    type,
                    R.string.needMoreCustomizationTitleLong,
                    R.string.needMoreCustomizationTitleShort,
                    R.string.needMoreCustomizationSubtitle
                )
            }

            APP_IS_SLOW_OR_BUGGY.also { type ->
                it[type] = FeedbackTypeMainReasonDisplay(
                    type,
                    R.string.appIsSlowOrBuggyTitleLong,
                    R.string.appIsSlowOrBuggyTitleShort,
                    R.string.appIsSlowOrBuggySubtitle
                )
            }

            OTHER.also { type ->
                it[type] = FeedbackTypeMainReasonDisplay(
                    type,
                    R.string.otherMainReasonTitleLong,
                    R.string.otherMainReasonTitleShort,
                    R.string.otherMainReasonSubtitle
                )
            }

        }

        val subReasons: Map<SubReason, FeedbackTypeSubReasonDisplay> = mutableMapOf<SubReason, FeedbackTypeSubReasonDisplay>().also {

            NAVIGATION_ISSUES.also { type ->
                it[type] = FeedbackTypeSubReasonDisplay(type, R.string.missingBrowserFeatureSubReasonNavigation)
            }

            TAB_MANAGEMENT.also { type ->
                it[type] = FeedbackTypeSubReasonDisplay(type, R.string.missingBrowserFeatureSubReasonTabManagement)
            }

            AD_POPUP_BLOCKING.also { type ->
                it[type] = FeedbackTypeSubReasonDisplay(type, R.string.missingBrowserFeatureSubReasonAdPopups)
            }

            WATCHING_VIDEOS.also { type ->
                it[type] = FeedbackTypeSubReasonDisplay(type, R.string.missingBrowserFeatureSubReasonVideos)
            }

            INTERACTING_IMAGES.also { type ->
                it[type] = FeedbackTypeSubReasonDisplay(type, R.string.missingBrowserFeatureSubReasonImages)
            }

            BOOKMARK_MANAGEMENT.also { type ->
                it[type] = FeedbackTypeSubReasonDisplay(type, R.string.missingBrowserFeatureSubReasonBookmarks)
            }

            MissingBrowserFeaturesSubReasons.OTHER.also { type ->
                it[type] = FeedbackTypeSubReasonDisplay(type, R.string.tellUsHowToImprove)
            }

            PROGRAMMING_TECHNICAL_SEARCHES.also { type ->
                it[type] = FeedbackTypeSubReasonDisplay(type, R.string.searchNotGoodEnoughSubReasonTechnicalSearches)
            }

            LAYOUT_MORE_LIKE_GOOGLE.also { type ->
                it[type] = FeedbackTypeSubReasonDisplay(type, R.string.searchNotGoodEnoughSubReasonGoogleLayout)
            }

            FASTER_LOAD_TIME.also { type ->
                it[type] = FeedbackTypeSubReasonDisplay(type, R.string.searchNotGoodEnoughSubReasonFasterLoadTimes)
            }

            SEARCHING_IN_SPECIFIC_LANGUAGE.also { type ->
                it[type] = FeedbackTypeSubReasonDisplay(type, R.string.searchNotGoodEnoughSubReasonSpecificLanguage)
            }

            BETTER_AUTOCOMPLETE.also { type ->
                it[type] = FeedbackTypeSubReasonDisplay(type, R.string.searchNotGoodEnoughSubReasonBetterAutocomplete)
            }

            SearchNotGoodEnoughSubReasons.OTHER.also { type ->
                it[type] = FeedbackTypeSubReasonDisplay(type, R.string.tellUsHowToImprove)
            }
        }

    }
}