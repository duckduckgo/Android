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
import androidx.annotation.StringRes
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
import kotlinx.android.synthetic.main.content_feedback_negative_disambiguation_sub_reason.*
import timber.log.Timber


class SubReasonNegativeFeedbackFragment : FeedbackFragment() {

    override val fragmentTag: String = "Disambiguation negative subreason feedback"

    private lateinit var recyclerAdapter: SubReasonAdapter

    interface DisambiguationNegativeFeedbackListener {
        fun userSelectedSubReasonMissingBrowser(type: FeedbackType.MissingBrowserFeaturesSubreasons)
        fun userSelectedSubReasonWebsitesNotLoading(type: FeedbackType.WebsitesNotLoading)
        fun userSelectedSubReasonSearchNotGoodEnough(type: FeedbackType.SearchResultsNotGoodEnough)
        fun userSelectedSubReasonNeedMoreCustomization(type: FeedbackType.NeedMoreCustomization)
        fun userSelectedSubReasonAppIsSlowOrBuggy(type: FeedbackType.AppIsSlowOrBuggy)
    }

    private val viewModel by bindViewModel<SubReasonNegativeFeedbackViewModel>()

    private val listener: DisambiguationNegativeFeedbackListener?
        get() = activity as DisambiguationNegativeFeedbackListener

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.content_feedback_negative_disambiguation_sub_reason, container, false)

        recyclerAdapter = SubReasonAdapter(object : (SubReasonDisplay) -> Unit {
            override fun invoke(reason: SubReasonDisplay) {
                Timber.i("Clicked reason: $reason")
                viewModel.userSelectedFeedbackType(reason.feedbackType)
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

                val mainReason = values()[args.getInt(MAIN_REASON_EXTRA)]
                val display = getDisplayTextForReasonType(mainReason)

                title.text = getString(display.title)
                subtitle.text = getString(display.subtitle)


                //val subreasons = activity?.resources?.getStringArray(args.getInt(SUBREASONS_EXTRA))?.toList() ?: emptyList()

                //title.text = getString(args.getInt(TITLE_EXTRA))
                //subtitle.text = getString(args.getInt(SUBTITLE_EXTRA))
                Timber.i("There are ${display.subReasons.size} subReasons to show")
                recyclerAdapter.submitList(display.subReasons)
            }
        }

    }

    private fun getDisplayTextForReasonType(mainReason: MainReason): Display {
        return when (mainReason) {
            MISSING_BROWSING_FEATURES -> Display(
                R.string.missingBrowserFeaturesTitleShort,
                R.string.missingBrowserFeaturesSubtitle,
                browserFeatureSubReasons()
            )
            SEARCH_NOT_GOOD_ENOUGH -> Display(
                R.string.searchNotGoodEnoughTitleShort,
                R.string.searchNotGoodEnoughSubtitle,
                searchNotGoodEnoughSubReasons()
            )
            NOT_ENOUGH_CUSOMIZATIONS -> Display(
                R.string.needMoreCustomizationTitleShort,
                R.string.needMoreCustomizationSubtitle,
                moreCustomizationsSubReasons()
            )
            APP_IS_SLOW_OR_BUGGY -> Display(
                R.string.appIsSlowOrBuggyTitleShort,
                R.string.appIsSlowOrBuggySubtitle,
                appSlowOrBuggySubReasons()
            )
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
            when (command) {
                is SubReasonNegativeFeedbackViewModel.Command.NavigateNegativeOpenEndedFeedbackScreen -> {
                    //listener?.userSelectedNegativeFeedbackTypeDisambiguationSubReason(command.type)
                }
            }
        })
    }

    data class Display(
        @StringRes val title: Int,
        @StringRes val subtitle: Int,
        val subReasons: List<SubReasonDisplay>
    )

    data class SubReasonDisplay(val feedbackType: SubReason, val displayString: String)

    companion object {

        private const val MAIN_REASON_EXTRA = "MAIN_REASON_EXTRA"

        fun instance(mainReason: MainReason): SubReasonNegativeFeedbackFragment {
            val fragment = SubReasonNegativeFeedbackFragment()
            fragment.arguments = Bundle().also {
                it.putInt(MAIN_REASON_EXTRA, mainReason.ordinal)
            }
            return fragment
        }

//        fun instance(@StringRes titleResId: Int, @StringRes subTitleResId: Int, @ArrayRes subreasonStringArrayId: Int): SubReasonNegativeFeedbackFragment {
//            val fragment = SubReasonNegativeFeedbackFragment()
//            fragment.arguments = Bundle().also {
//                it.putInt(TITLE_EXTRA, titleResId)
//                it.putInt(SUBTITLE_EXTRA, subTitleResId)
//                it.putInt(SUBREASONS_EXTRA, subreasonStringArrayId)
//            }
//            return fragment
    }
}
