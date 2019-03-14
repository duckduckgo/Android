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

package com.duckduckgo.app.feedback.ui.common

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.feedback.ui.common.FragmentState.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackType
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MainReason
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.SubReason
import com.duckduckgo.app.global.SingleLiveEvent
import timber.log.Timber


class FeedbackViewModel : ViewModel() {

    val viewState: MutableLiveData<ViewState> = MutableLiveData()

    init {
        //viewState.value = ViewState(fragmentViewState = InitialAppEnjoymentClarifier(NAVIGATION_FORWARDS))
        //viewState.value = ViewState(fragmentViewState = FragmentState.PositiveShareFeedback(NAVIGATION_FORWARDS))
        //viewState.value = ViewState(fragmentViewState = FragmentState.NegativeFeedbackMainReason(NAVIGATION_FORWARDS))
        viewState.value = ViewState(fragmentViewState = FragmentState.NegativeFeedbackSubReason(NAVIGATION_FORWARDS, MainReason.MISSING_BROWSING_FEATURES))
    }

    private val currentViewState: ViewState
        get() = viewState.value!!

    val command: SingleLiveEvent<Command> = SingleLiveEvent()


    fun userSelectedNegativeFeedbackMainReason(mainReason: MainReason) {
        val newState = when (mainReason) {
            MainReason.MISSING_BROWSING_FEATURES -> NegativeFeedbackSubReason(NAVIGATION_FORWARDS, mainReason)
            MainReason.WEBSITES_NOT_LOADING -> NegativeWebSitesBrokenFeedback(NAVIGATION_FORWARDS, mainReason)
            MainReason.SEARCH_NOT_GOOD_ENOUGH -> NegativeFeedbackSubReason(NAVIGATION_FORWARDS, mainReason)
            MainReason.NOT_ENOUGH_CUSTOMIZATIONS -> NegativeFeedbackSubReason(NAVIGATION_FORWARDS, mainReason)
            MainReason.APP_IS_SLOW_OR_BUGGY -> NegativeFeedbackSubReason(NAVIGATION_FORWARDS, mainReason)
            MainReason.OTHER -> NegativeOpenEndedFeedback(NAVIGATION_FORWARDS, mainReason)
        }
        viewState.value = currentViewState.copy(
            fragmentViewState = newState,
            mainReason = mainReason,
            subReason = null,
            previousViewState = currentViewState.fragmentViewState
        )
    }


    fun onBackPressed() {
        when (currentViewState.fragmentViewState) {
            is InitialAppEnjoymentClarifier -> {
                command.value = Command.Exit(feedbackSubmitted = false)
            }
            is PositiveFeedbackStep1 -> {
                viewState.value = currentViewState.copy(fragmentViewState = InitialAppEnjoymentClarifier(NAVIGATION_BACKWARDS))
            }
            is PositiveShareFeedback -> {
                viewState.value = currentViewState.copy(fragmentViewState = PositiveFeedbackStep1(NAVIGATION_BACKWARDS))
            }
            is NegativeFeedbackMainReason -> {
                viewState.value = currentViewState.copy(fragmentViewState = InitialAppEnjoymentClarifier(NAVIGATION_BACKWARDS))
            }
            is NegativeFeedbackSubReason -> {
                viewState.value = currentViewState.copy(fragmentViewState = NegativeFeedbackMainReason(NAVIGATION_BACKWARDS))
            }
            is NegativeWebSitesBrokenFeedback -> {
                viewState.value = currentViewState.copy(fragmentViewState = NegativeFeedbackMainReason(NAVIGATION_BACKWARDS))
            }
            is NegativeOpenEndedFeedback -> {
                val newViewState = when (currentViewState.previousViewState) {
                    is NegativeFeedbackSubReason -> {
                        NegativeFeedbackSubReason(NAVIGATION_BACKWARDS, currentViewState.mainReason!!)
                    }
                    is NegativeFeedbackMainReason -> {
                        NegativeFeedbackMainReason(NAVIGATION_BACKWARDS)
                    }
                    else -> {
                        NegativeFeedbackMainReason(NAVIGATION_BACKWARDS)
                    }
                }
                viewState.value = currentViewState.copy(fragmentViewState = newViewState)
            }
        }
    }

    fun userSelectedPositiveFeedback() {
        viewState.value = currentViewState.copy(
            fragmentViewState = PositiveFeedbackStep1(NAVIGATION_FORWARDS),
            previousViewState = currentViewState.fragmentViewState
        )
    }

    fun userSelectedNegativeFeedback() {
        viewState.value = currentViewState.copy(
            fragmentViewState = NegativeFeedbackMainReason(NAVIGATION_FORWARDS),
            previousViewState = currentViewState.fragmentViewState
        )
    }

    fun userWantsToCancel() {
        Timber.i("User is cancelling")
        command.value = Command.Exit(feedbackSubmitted = false)
    }

    fun userSelectedToGiveFeedback() {
        Timber.i("User gave feedback")
        viewState.value = currentViewState.copy(
            fragmentViewState = PositiveShareFeedback(NAVIGATION_FORWARDS),
            previousViewState = currentViewState.fragmentViewState
        )
    }

    fun onProvidedNegativeOpenEndedFeedback(feedback: String) {
        Timber.i("User provided negative feedback: {$feedback}")
        command.value = Command.Exit(feedbackSubmitted = true)
    }

    fun onProvidedBrokenSiteFeedback(feedback: String, brokenSite: String?) {
        command.value = Command.Exit(feedbackSubmitted = true)
    }

    fun onProvidedPositiveOpenEndedFeedback(feedback: String) {
        Timber.i("User provided positive feedback: {$feedback}")
        command.value = Command.Exit(feedbackSubmitted = true)
    }

    fun userSelectedNegativeFeedbackMissingBrowserSubReason(mainReason: MainReason, subReason: FeedbackType.MissingBrowserFeaturesSubReasons) {
        val newState = NegativeOpenEndedFeedback(NAVIGATION_FORWARDS, mainReason, subReason)
        viewState.value = currentViewState.copy(
            fragmentViewState = newState,
            mainReason = mainReason,
            subReason = subReason,
            previousViewState = currentViewState.fragmentViewState
        )
    }

    fun userSelectedSubReasonSearchNotGoodEnough(mainReason: MainReason, subReason: FeedbackType.SearchNotGoodEnoughSubReasons) {
        val newState = NegativeOpenEndedFeedback(NAVIGATION_FORWARDS, mainReason, subReason)
        viewState.value = currentViewState.copy(
            fragmentViewState = newState,
            mainReason = mainReason,
            subReason = subReason,
            previousViewState = currentViewState.fragmentViewState
        )
    }

    fun userSelectedSubReasonNeedMoreCustomization(mainReason: MainReason, subReason: FeedbackType.CustomizationSubReasons) {
        val newState = NegativeOpenEndedFeedback(NAVIGATION_FORWARDS, mainReason, subReason)
        viewState.value = currentViewState.copy(
            fragmentViewState = newState,
            mainReason = mainReason,
            subReason = subReason,
            previousViewState = currentViewState.fragmentViewState
        )
    }

    fun userSelectedSubReasonAppIsSlowOrBuggy(mainReason: MainReason, subReason: FeedbackType.PerformanceSubReasons) {
        val newState = NegativeOpenEndedFeedback(NAVIGATION_FORWARDS, mainReason, subReason)
        viewState.value = currentViewState.copy(
            fragmentViewState = newState,
            mainReason = mainReason,
            subReason = subReason,
            previousViewState = currentViewState.fragmentViewState
        )
    }

    companion object {
        val NAVIGATION_FORWARDS = NavigationDirection(true)
        val NAVIGATION_BACKWARDS = NavigationDirection(false)
    }
}

data class ViewState(
    val fragmentViewState: FragmentState,
    val previousViewState: FragmentState? = null,
    val mainReason: MainReason? = null,
    val subReason: SubReason? = null
)

sealed class FragmentState(open val direction: NavigationDirection) {
    data class InitialAppEnjoymentClarifier(override val direction: NavigationDirection) : FragmentState(direction)

    // positive flow
    data class PositiveFeedbackStep1(override val direction: NavigationDirection) : FragmentState(direction)

    data class PositiveShareFeedback(override val direction: NavigationDirection) : FragmentState(direction)

    // negative flow
    data class NegativeFeedbackMainReason(override val direction: NavigationDirection) : FragmentState(direction)
    data class NegativeFeedbackSubReason(override val direction: NavigationDirection, val mainReason: MainReason) : FragmentState(direction)
    data class NegativeOpenEndedFeedback(override val direction: NavigationDirection, val mainReason: MainReason, val subReason: SubReason? = null) :
        FragmentState(direction)

    data class NegativeWebSitesBrokenFeedback(
        override val direction: NavigationDirection,
        val mainReason: MainReason,
        val subReason: SubReason? = null
    ) : FragmentState(direction)
}

inline class NavigationDirection(val isForward: Boolean)

sealed class Command {
    data class Exit(val feedbackSubmitted: Boolean) : Command()
}