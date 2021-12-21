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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.test.annotation.UiThreadTest
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.feedback.api.FeedbackSubmitter
import com.duckduckgo.app.feedback.ui.common.FragmentState.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MainReason.*
import com.duckduckgo.app.feedback.ui.negative.FeedbackType.MissingBrowserFeaturesSubReasons.TAB_MANAGEMENT
import com.duckduckgo.app.playstore.PlayStoreUtils
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
@Suppress("RemoveExplicitTypeArguments")
class FeedbackViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var testee: FeedbackViewModel

    private val playStoreUtils: PlayStoreUtils = mock()
    private val feedbackSubmitter: FeedbackSubmitter = mock()

    private val commandObserver: Observer<Command> = mock()
    private val commandCaptor = argumentCaptor<Command>()

    private val updateViewCommand
        get() = testee.updateViewCommand.value?.fragmentViewState

    @ExperimentalCoroutinesApi
    @Before
    @UiThreadTest
    fun setup() {
        testee = FeedbackViewModel(playStoreUtils, feedbackSubmitter, TestScope(), coroutineRule.testDispatcherProvider)
        testee.command.observeForever(commandObserver)
    }

    @After
    @UiThreadTest
    fun tearDown() {
        testee.command.removeObserver(commandObserver)
    }

    @UiThreadTest
    @Test
    fun whenInitialisedThenFragmentStateIsForFirstStep() {
        assertTrue(updateViewCommand is InitialAppEnjoymentClarifier)
    }

    @Test
    @UiThreadTest
    fun whenCanRateAppAndUserSelectsInitialHappyFaceThenFragmentStateIsFirstStepOfHappyFlow() {
        configureRatingCanBeGiven()
        testee.userSelectedPositiveFeedback()
        assertTrue(updateViewCommand is FragmentState.PositiveFeedbackFirstStep)
        verifyForwardsNavigation(updateViewCommand)
    }

    @Test
    @UiThreadTest
    fun whenCannotRateAppAndUserSelectsInitialHappyFaceThenFragmentStateSkipsStraightToSharingFeedback() {
        configureRatingCannotBeGiven()
        testee.userSelectedPositiveFeedback()
        assertTrue(updateViewCommand is FragmentState.PositiveShareFeedback)
        verifyForwardsNavigation(updateViewCommand)
    }

    @Test
    @UiThreadTest
    fun whenCanRateAppAndUserNavigatesBackFromPositiveInitialFragmentThenFragmentStateIsInitialFragment() {
        configureRatingCanBeGiven()
        testee.userSelectedPositiveFeedback()
        testee.onBackPressed()

        assertTrue(updateViewCommand is InitialAppEnjoymentClarifier)
        verifyBackwardsNavigation(updateViewCommand)
    }

    @Test
    @UiThreadTest
    fun whenCannotRateAppAndUserNavigatesBackFromPositiveInitialFragmentThenFragmentStateIsInitialFragment() {
        configureRatingCannotBeGiven()
        testee.userSelectedPositiveFeedback()
        testee.onBackPressed()

        assertTrue(updateViewCommand is InitialAppEnjoymentClarifier)
    }

    @Test
    fun whenUserChoosesNotToProvideFurtherDetailsForPositiveFeedbackThenSubmitted() = runTest {
        testee.userGavePositiveFeedbackNoDetails()

        verify(feedbackSubmitter).sendPositiveFeedback(null)
    }

    @Test
    fun whenUserChoosesNotToProvideFurtherDetailsForPositiveFeedbackThenExitCommandIssued() = runTest {
        testee.userGavePositiveFeedbackNoDetails()

        val command = captureCommand() as Command.Exit
        assertTrue(command.feedbackSubmitted)
    }

    @Test
    fun whenUserProvidesFurtherDetailsForPositiveFeedbackThenFeedbackSubmitted() = runTest {
        testee.userProvidedPositiveOpenEndedFeedback("foo")

        verify(feedbackSubmitter).sendPositiveFeedback("foo")
    }

    @Test
    fun whenUserProvidesFurtherDetailsForPositiveFeedbackThenExitCommandIssued() = runTest {
        testee.userProvidedPositiveOpenEndedFeedback("foo")

        val command = captureCommand() as Command.Exit
        assertTrue(command.feedbackSubmitted)
    }

    @Test
    fun whenUserProvidesNegativeFeedbackThenFeedbackSubmitted() = runTest {
        testee.userProvidedNegativeOpenEndedFeedback(MISSING_BROWSING_FEATURES, TAB_MANAGEMENT, "foo")
        verify(feedbackSubmitter).sendNegativeFeedback(MISSING_BROWSING_FEATURES, TAB_MANAGEMENT, "foo")
    }

    @Test
    fun whenUserProvidesNegativeFeedbackNoSubReasonThenFeedbackSubmitted() = runTest {
        testee.userProvidedNegativeOpenEndedFeedback(MISSING_BROWSING_FEATURES, null, "foo")
        verify(feedbackSubmitter).sendNegativeFeedback(MISSING_BROWSING_FEATURES, null, "foo")
    }

    @Test
    fun whenUserProvidesNegativeFeedbackEmptyOpenEndedFeedbackThenFeedbackSubmitted() = runTest {
        testee.userProvidedNegativeOpenEndedFeedback(MISSING_BROWSING_FEATURES, null, "")
        verify(feedbackSubmitter).sendNegativeFeedback(MISSING_BROWSING_FEATURES, null, "")
    }

    @Test
    @UiThreadTest
    fun whenUserCancelsThenExitCommandIssued() {
        testee.userWantsToCancel()
        val command = captureCommand() as Command.Exit
        assertFalse(command.feedbackSubmitted)
    }

    @Test
    @UiThreadTest
    fun whenUserSelectsInitialSadFaceThenFragmentStateIsFirstStepOfUnhappyFlow() {
        testee.userSelectedNegativeFeedback()
        assertTrue(updateViewCommand is NegativeFeedbackMainReason)
        verifyForwardsNavigation(updateViewCommand)
    }

    @Test
    @UiThreadTest
    fun whenUserNavigatesBackFromNegativeMainReasonFragmentThenFragmentStateIsInitialFragment() {
        testee.userSelectedNegativeFeedback()
        testee.onBackPressed()
        assertTrue(updateViewCommand is InitialAppEnjoymentClarifier)
        verifyBackwardsNavigation(updateViewCommand)
    }

    @Test
    @UiThreadTest
    fun whenUserSelectsMainNegativeReasonMissingBrowserFeaturesThenFragmentStateIsSubReasonSelection() {
        testee.userSelectedNegativeFeedbackMainReason(MISSING_BROWSING_FEATURES)
        assertTrue(updateViewCommand is NegativeFeedbackSubReason)
        verifyForwardsNavigation(updateViewCommand)
    }

    @Test
    @UiThreadTest
    fun whenUserSelectsMainNegativeReasonNotEnoughCustomizationsThenFragmentStateIsSubReasonSelection() {
        testee.userSelectedNegativeFeedbackMainReason(NOT_ENOUGH_CUSTOMIZATIONS)
        assertTrue(updateViewCommand is NegativeFeedbackSubReason)
        verifyForwardsNavigation(updateViewCommand)
    }

    @Test
    @UiThreadTest
    fun whenUserSelectsMainNegativeReasonSearchNotGoodEnoughThenFragmentStateIsSubReasonSelection() {
        testee.userSelectedNegativeFeedbackMainReason(SEARCH_NOT_GOOD_ENOUGH)
        assertTrue(updateViewCommand is NegativeFeedbackSubReason)
        verifyForwardsNavigation(updateViewCommand)
    }

    @Test
    @UiThreadTest
    fun whenUserSelectsMainNegativeReasonAppIsSlowOrBuggyThenFragmentStateIsSubReasonSelection() {
        testee.userSelectedNegativeFeedbackMainReason(APP_IS_SLOW_OR_BUGGY)
        assertTrue(updateViewCommand is NegativeFeedbackSubReason)
        verifyForwardsNavigation(updateViewCommand)
    }

    @Test
    @UiThreadTest
    fun whenUserSelectsMainNegativeReasonOtherThenFragmentStateIsOpenEndedFeedback() {
        testee.userSelectedNegativeFeedbackMainReason(OTHER)
        assertTrue(updateViewCommand is NegativeOpenEndedFeedback)
        verifyForwardsNavigation(updateViewCommand)
    }

    @Test
    @UiThreadTest
    fun whenUserSelectsMainNegativeReasonBrokenSiteThenFragmentStateIsSubReasonSelection() {
        testee.userSelectedNegativeFeedbackMainReason(WEBSITES_NOT_LOADING)
        assertTrue(updateViewCommand is NegativeWebSitesBrokenFeedback)
        verifyForwardsNavigation(updateViewCommand)
    }

    @Test
    @UiThreadTest
    fun whenUserSelectsSubNegativeReasonThenFragmentStateIsOpenEndedFeedback() {
        testee.userSelectedSubReasonMissingBrowserFeatures(MISSING_BROWSING_FEATURES, TAB_MANAGEMENT)
        assertTrue(updateViewCommand is NegativeOpenEndedFeedback)
        verifyForwardsNavigation(updateViewCommand)
    }

    @Test
    @UiThreadTest
    fun whenUserNavigatesBackFromSubReasonSelectionThenFragmentStateIsMainReasonSelection() {
        testee.userSelectedNegativeFeedbackMainReason(MISSING_BROWSING_FEATURES)
        testee.onBackPressed()
        assertTrue(updateViewCommand is NegativeFeedbackMainReason)
        verifyBackwardsNavigation(updateViewCommand)
    }

    @Test
    @UiThreadTest
    fun whenUserNavigatesBackFromOpenEndedFeedbackAndSubReasonIsValidStepThenFragmentStateIsSubReasonSelection() {
        testee.userSelectedNegativeFeedbackMainReason(MISSING_BROWSING_FEATURES)
        testee.userSelectedSubReasonMissingBrowserFeatures(MISSING_BROWSING_FEATURES, TAB_MANAGEMENT)
        testee.onBackPressed()
        assertTrue(updateViewCommand is NegativeFeedbackSubReason)
        verifyBackwardsNavigation(updateViewCommand)
    }

    @Test
    @UiThreadTest
    fun whenUserNavigatesBackFromOpenEndedFeedbackAndSubReasonNotAValidStepThenFragmentStateIsMainReasonSelection() {
        testee.userSelectedNegativeFeedbackMainReason(OTHER)
        testee.onBackPressed()
        assertTrue(updateViewCommand is NegativeFeedbackMainReason)
        verifyBackwardsNavigation(updateViewCommand)
    }

    @Test
    @UiThreadTest
    fun whenUserNavigatesBackFromOpenEndedFeedbackThenFragmentStateIsSubReasonSelection() {
        testee.userSelectedNegativeFeedbackMainReason(MISSING_BROWSING_FEATURES)
        testee.userSelectedSubReasonMissingBrowserFeatures(MISSING_BROWSING_FEATURES, TAB_MANAGEMENT)
        testee.onBackPressed()
        assertTrue(updateViewCommand is NegativeFeedbackSubReason)
        verifyBackwardsNavigation(updateViewCommand)
    }

    private fun verifyForwardsNavigation(fragmentViewState: FragmentState?) {
        assertTrue(fragmentViewState?.forwardDirection == true)
    }

    private fun verifyBackwardsNavigation(fragmentViewState: FragmentState?) {
        assertTrue(fragmentViewState?.forwardDirection == false)
    }

    private fun captureCommand(): Command {
        verify(commandObserver).onChanged(commandCaptor.capture())
        return commandCaptor.lastValue
    }

    private fun configureRatingCanBeGiven() {
        whenever(playStoreUtils.installedFromPlayStore()).thenReturn(true)
        whenever(playStoreUtils.isPlayStoreInstalled()).thenReturn(true)
    }

    private fun configureRatingCannotBeGiven() {
        whenever(playStoreUtils.installedFromPlayStore()).thenReturn(false)
        whenever(playStoreUtils.isPlayStoreInstalled()).thenReturn(false)
    }
}
