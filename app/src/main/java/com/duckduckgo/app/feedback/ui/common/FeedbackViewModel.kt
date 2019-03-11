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
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MissingBrowserFeaturesSubreasons
import com.duckduckgo.app.global.SingleLiveEvent
import timber.log.Timber


class FeedbackViewModel : ViewModel() {

    val viewState: MutableLiveData<ViewState> = MutableLiveData()

    init {
        //viewState.value = ViewState(fragmentViewState = InitialAppEnjoymentClarifier(NAVIGATION_FORWARDS))
        //viewState.value = ViewState(fragmentViewState = FragmentState.PositiveShareFeedback(NAVIGATION_FORWARDS))
        viewState.value = ViewState(fragmentViewState = FragmentState.NegativeFeedbackMainReason(NAVIGATION_FORWARDS))
    }

    private val currentViewState: ViewState
        get() = viewState.value!!

    val command: SingleLiveEvent<Command> = SingleLiveEvent()


    fun onBackPressed() {
        Timber.i("On back button press")

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
            else -> {
                Timber.w("Still needs wired up")
            }
        }
    }


    fun userSelectedPositiveFeedback() {
        viewState.value = currentViewState.copy(fragmentViewState = PositiveFeedbackStep1(NAVIGATION_FORWARDS))
    }

    fun userSelectedNegativeFeedback() {
        viewState.value = currentViewState.copy(fragmentViewState = NegativeFeedbackMainReason(NAVIGATION_FORWARDS))
    }

    fun userWantsToCancel() {
        Timber.i("User is cancelling")
        command.value = Command.Exit(feedbackSubmitted = false)
    }

    fun userSelectedToGiveFeedback() {
        Timber.i("User gave feedback")
        viewState.value = currentViewState.copy(fragmentViewState = PositiveShareFeedback(NAVIGATION_FORWARDS))
    }

    fun userProvidedOpenEndedFeedback(feedback: String) {
        Timber.i("User provided feedback: {$feedback}")
        command.value = Command.Exit(feedbackSubmitted = true)
    }

    fun userSelectedNegativeFeedbackMainReason(type: MainReason) {
        val newState = NegativeFeedbackSubReason(NAVIGATION_FORWARDS, type)
        viewState.value = currentViewState.copy(fragmentViewState = newState)
    }

    fun userSelectedNegativeFeedbackMissingBrowserSubReason(type: MissingBrowserFeaturesSubreasons) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun userSelectedSubReasonWebsitesNotLoading(type: FeedbackType.WebsitesNotLoading) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun userSelectedSubReasonSearchNotGoodEnough(type: FeedbackType.SearchResultsNotGoodEnough) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun userSelectedSubReasonNeedMoreCustomization(type: FeedbackType.NeedMoreCustomization) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    fun userSelectedSubReasonAppIsSlowOrBuggy(type: FeedbackType.AppIsSlowOrBuggy) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    companion object {
        val NAVIGATION_FORWARDS = NavigationDirection(true)
        val NAVIGATION_BACKWARDS = NavigationDirection(false)
    }
}

data class ViewState(
    val fragmentViewState: FragmentState
)

sealed class FragmentState(open val direction: NavigationDirection) {
    data class InitialAppEnjoymentClarifier(override val direction: NavigationDirection) : FragmentState(direction)

    // positive flow
    data class PositiveFeedbackStep1(override val direction: NavigationDirection) : FragmentState(direction)

    data class PositiveShareFeedback(override val direction: NavigationDirection) : FragmentState(direction)

    // negative flow
    data class NegativeFeedbackMainReason(override val direction: NavigationDirection) : FragmentState(direction)
    data class NegativeFeedbackSubReason(override val direction: NavigationDirection, val mainReason: MainReason) : FragmentState(direction)
    data class NegativeOpenEndedFeedback(override val direction: NavigationDirection, val mainReason: MainReason) : FragmentState(direction)

}

inline class NavigationDirection(val isForward: Boolean)

sealed class Command {
    data class Exit(val feedbackSubmitted: Boolean) : Command()
}