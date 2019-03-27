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

package com.duckduckgo.app.feedback.ui.negative.subreason

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.feedback.ui.common.FeedbackFragment
import com.duckduckgo.app.feedback.ui.common.FeedbackItemDecoration
import com.duckduckgo.app.feedback.ui.negative.FeedbackType
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MainReason.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackTypeDisplay
import com.duckduckgo.app.feedback.ui.negative.FeedbackTypeDisplay.FeedbackTypeSubReasonDisplay
import com.duckduckgo.app.feedback.ui.negative.displayText
import kotlinx.android.synthetic.main.content_feedback_negative_disambiguation_sub_reason.*
import timber.log.Timber


class SubReasonNegativeFeedbackFragment : FeedbackFragment() {

    private lateinit var recyclerAdapter: SubReasonAdapter

    interface DisambiguationNegativeFeedbackListener {
        fun userSelectedSubReasonMissingBrowserFeatures(mainReason: MainReason, subReason: FeedbackType.MissingBrowserFeaturesSubReasons)
        fun userSelectedSubReasonSearchNotGoodEnough(mainReason: MainReason, subReason: FeedbackType.SearchNotGoodEnoughSubReasons)
        fun userSelectedSubReasonNeedMoreCustomization(mainReason: MainReason, subReason: FeedbackType.CustomizationSubReasons)
        fun userSelectedSubReasonAppIsSlowOrBuggy(mainReason: MainReason, subReason: FeedbackType.PerformanceSubReasons)
    }

    private val listener: DisambiguationNegativeFeedbackListener?
        get() = activity as DisambiguationNegativeFeedbackListener

    private lateinit var mainReason: MainReason

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.content_feedback_negative_disambiguation_sub_reason, container, false)

        recyclerAdapter = SubReasonAdapter(object : (FeedbackTypeSubReasonDisplay) -> Unit {
            override fun invoke(reason: FeedbackTypeSubReasonDisplay) {
                when (reason.subReason) {
                    is MissingBrowserFeaturesSubReasons -> {
                        listener?.userSelectedSubReasonMissingBrowserFeatures(mainReason, reason.subReason)
                    }
                    is SearchNotGoodEnoughSubReasons -> {
                        listener?.userSelectedSubReasonSearchNotGoodEnough(mainReason, reason.subReason)
                    }
                    is CustomizationSubReasons -> {
                        listener?.userSelectedSubReasonNeedMoreCustomization(mainReason, reason.subReason)
                    }
                    is PerformanceSubReasons -> {
                        listener?.userSelectedSubReasonAppIsSlowOrBuggy(mainReason, reason.subReason)
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
            recyclerView.addItemDecoration(FeedbackItemDecoration(ContextCompat.getDrawable(it, R.drawable.feedback_list_divider)!!))


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

    private fun getDisplayTextForReasonType(mainReason: MainReason): List<FeedbackTypeSubReasonDisplay> {
        return when (mainReason) {
            MISSING_BROWSING_FEATURES -> browserFeatureSubReasons()
            SEARCH_NOT_GOOD_ENOUGH -> searchNotGoodEnoughSubReasons()
            NOT_ENOUGH_CUSTOMIZATIONS -> moreCustomizationsSubReasons()
            APP_IS_SLOW_OR_BUGGY -> appSlowOrBuggySubReasons()
            else -> throw IllegalStateException("Not handled - $mainReason")
        }
    }

    private fun browserFeatureSubReasons(): List<FeedbackTypeSubReasonDisplay> {
        return MissingBrowserFeaturesSubReasons.values().mapNotNull {
            FeedbackTypeDisplay.subReasons[it]
        }
    }

    private fun searchNotGoodEnoughSubReasons(): List<FeedbackTypeSubReasonDisplay> {
        return SearchNotGoodEnoughSubReasons.values().mapNotNull {
            FeedbackTypeDisplay.subReasons[it]
        }
    }

    private fun moreCustomizationsSubReasons(): List<FeedbackTypeSubReasonDisplay> {
        return FeedbackType.CustomizationSubReasons.values().mapNotNull {
            FeedbackTypeDisplay.subReasons[it]
        }
    }

    private fun appSlowOrBuggySubReasons(): List<FeedbackTypeSubReasonDisplay> {
        return FeedbackType.PerformanceSubReasons.values().mapNotNull {
            FeedbackTypeDisplay.subReasons[it]
        }
    }

    override fun configureViewModelObservers() {}

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
