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

package com.duckduckgo.app.feedback.ui.negative.initial

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.feedback.ui.common.FeedbackFragment
import com.duckduckgo.app.feedback.ui.common.FeedbackItemDecoration
import com.duckduckgo.app.feedback.ui.negative.FeedbackType
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.CustomizationSubReasons.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MainReason.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MissingBrowserFeaturesSubReasons.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.PerformanceSubReasons.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.SearchNotGoodEnoughSubReasons.*
import com.duckduckgo.app.feedback.ui.negative.displayText
import kotlinx.android.synthetic.main.content_feedback_negative_disambiguation_sub_reason.*
import timber.log.Timber


class SubReasonNegativeFeedbackFragment : FeedbackFragment() {

    override val fragmentTag: String = "Disambiguation negative subreason feedback"

    private lateinit var recyclerAdapter: SubReasonAdapter

    interface DisambiguationNegativeFeedbackListener {
        fun userSelectedSubReasonMissingBrowserFeatures(mainReason: MainReason, subReason: FeedbackType.MissingBrowserFeaturesSubReasons)
        fun userSelectedSubReasonSearchNotGoodEnough(mainReason: MainReason, subReason: FeedbackType.SearchNotGoodEnoughSubReasons)
        fun userSelectedSubReasonNeedMoreCustomization(mainReason: MainReason, subReason: FeedbackType.CustomizationSubReasons)
        fun userSelectedSubReasonAppIsSlowOrBuggy(mainReason: MainReason, subReason: FeedbackType.PerformanceSubReasons)
    }

    private val viewModel by bindViewModel<SubReasonNegativeFeedbackViewModel>()

    private val listener: DisambiguationNegativeFeedbackListener?
        get() = activity as DisambiguationNegativeFeedbackListener

    private lateinit var mainReason: MainReason

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.content_feedback_negative_disambiguation_sub_reason, container, false)

        recyclerAdapter = SubReasonAdapter(object : (SubReasonDisplay) -> Unit {
            override fun invoke(reason: SubReasonDisplay) {
                Timber.i("Clicked reason: $reason")
                when (reason.feedbackType) {
                    is MissingBrowserFeaturesSubReasons -> {
                        listener?.userSelectedSubReasonMissingBrowserFeatures(mainReason, reason.feedbackType)
                    }
                    is SearchNotGoodEnoughSubReasons -> {
                        listener?.userSelectedSubReasonSearchNotGoodEnough(mainReason, reason.feedbackType)
                    }
                    is CustomizationSubReasons -> {
                        listener?.userSelectedSubReasonNeedMoreCustomization(mainReason, reason.feedbackType)
                    }
                    is PerformanceSubReasons -> {
                        listener?.userSelectedSubReasonAppIsSlowOrBuggy(mainReason, reason.feedbackType)
                    }
                }
            }
        })

        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        activity?.let {
            recyclerView.layoutManager = LinearLayoutManager(it)
            recyclerView.adapter = recyclerAdapter
            recyclerView.addItemDecoration(FeedbackItemDecoration(ContextCompat.getDrawable(it, R.drawable.feedback_list_divider)))


            arguments?.let { args ->

                mainReason = args.getSerializable(MAIN_REASON_EXTRA) as MainReason
                val display = mainReason.displayText()

                title.text = getString(display!!.titleDisplayResId)
                subtitle.text = getString(display.subtitleDisplayResId)

                val subReasons = getDisplayTextForReasonType(mainReason)
                Timber.i("There are ${subReasons.size} subReasons to show")
                recyclerAdapter.submitList(subReasons)
            }
        }
    }

    private fun getDisplayTextForReasonType(mainReason: MainReason): List<SubReasonDisplay> {
        return when (mainReason) {
            MISSING_BROWSING_FEATURES -> browserFeatureSubReasons()
            SEARCH_NOT_GOOD_ENOUGH -> searchNotGoodEnoughSubReasons()
            NOT_ENOUGH_CUSTOMIZATIONS -> moreCustomizationsSubReasons()
            APP_IS_SLOW_OR_BUGGY -> appSlowOrBuggySubReasons()
            else -> throw IllegalStateException("Not handled - $mainReason")
        }
    }

    private fun browserFeatureSubReasons(): List<SubReasonDisplay> {
        return listOf(
            SubReasonDisplay(NAVIGATION_ISSUES, getString(R.string.missingBrowserFeatureSubReasonNavigation)),
            SubReasonDisplay(TAB_MANAGEMENT, getString(R.string.missingBrowserFeatureSubReasonTabManagement)),
            SubReasonDisplay(AD_POPUP_BLOCKING, getString(R.string.missingBrowserFeatureSubReasonAdPopups)),
            SubReasonDisplay(WATCHING_VIDEOS, getString(R.string.missingBrowserFeatureSubReasonVideos)),
            SubReasonDisplay(INTERACTING_IMAGES, getString(R.string.missingBrowserFeatureSubReasonImages)),
            SubReasonDisplay(BOOKMARK_MANAGEMENT, getString(R.string.missingBrowserFeatureSubReasonBookmarks)),
            SubReasonDisplay(MissingBrowserFeaturesSubReasons.OTHER, getString(R.string.missingBrowserFeatureSubReasonOther))
        )
    }

    private fun searchNotGoodEnoughSubReasons(): List<SubReasonDisplay> {
        return listOf(
            SubReasonDisplay(PROGRAMMING_TECHNICAL_SEARCHES, getString(R.string.searchNotGoodEnoughSubReasonTechnicalSearches)),
            SubReasonDisplay(LAYOUT_MORE_LIKE_GOOGLE, getString(R.string.searchNotGoodEnoughSubReasonGoogleLayout)),
            SubReasonDisplay(FASTER_LOAD_TIME, getString(R.string.searchNotGoodEnoughSubReasonFasterLoadTimes)),
            SubReasonDisplay(SEARCHING_IN_SPECIFIC_LANGUAGE, getString(R.string.searchNotGoodEnoughSubReasonSpecificLanguage)),
            SubReasonDisplay(BETTER_AUTOCOMPLETE, getString(R.string.searchNotGoodEnoughSubReasonBetterAutocomplete)),
            SubReasonDisplay(SearchNotGoodEnoughSubReasons.OTHER, getString(R.string.searchNotGoodEnoughSubReasonOther))
        )
    }

    private fun moreCustomizationsSubReasons(): List<SubReasonDisplay> {
        return listOf(
            SubReasonDisplay(HOME_SCREEN_CONFIGURATION, getString(R.string.needMoreCustomizationSubReasonHomeScreenConfiguration)),
            SubReasonDisplay(TAB_DISPLAY, getString(R.string.needMoreCustomizationSubReasonTabDisplay)),
            SubReasonDisplay(HOW_APP_LOOKS, getString(R.string.needMoreCustomizationSubReasonAppLooks)),
            SubReasonDisplay(WHICH_DATA_IS_CLEARED, getString(R.string.needMoreCustomizationSubReasonWhichDataIsCleared)),
            SubReasonDisplay(WHEN_DATA_IS_CLEARED, getString(R.string.needMoreCustomizationSubReasonWhenDataIsCleared)),
            SubReasonDisplay(BOOKMARK_DISPLAY, getString(R.string.needMoreCustomizationSubReasonBookmarksDisplay)),
            SubReasonDisplay(SearchNotGoodEnoughSubReasons.OTHER, getString(R.string.needMoreCustomizationSubReasonOther))
        )
    }

    private fun appSlowOrBuggySubReasons(): List<SubReasonDisplay> {
        return listOf(
            SubReasonDisplay(SLOW_WEB_PAGE_LOADS, getString(R.string.appIsSlowOrBuggySubReasonSlowResults)),
            SubReasonDisplay(APP_CRASHES_OR_FREEZES, getString(R.string.appIsSlowOrBuggySubReasonAppCrashesOrFreezes)),
            SubReasonDisplay(MEDIA_PLAYBACK_BUGS, getString(R.string.appIsSlowOrBuggySubReasonMediaPlayback)),
            SubReasonDisplay(PerformanceSubReasons.OTHER, getString(R.string.appIsSlowOrBuggySubReasonOther))
        )
    }


    override fun configureViewModelObservers() {
        viewModel.command.observe(this, Observer { command ->

        })
    }

    data class SubReasonDisplay(val feedbackType: SubReason, val displayString: String)

    companion object {

        private const val MAIN_REASON_EXTRA = "MAIN_REASON_EXTRA"

        fun instance(mainReason: MainReason): SubReasonNegativeFeedbackFragment {
            val fragment = SubReasonNegativeFeedbackFragment()
            fragment.arguments = Bundle().also {
                it.putSerializable(MAIN_REASON_EXTRA, mainReason)
            }
            return fragment
        }
    }
}
