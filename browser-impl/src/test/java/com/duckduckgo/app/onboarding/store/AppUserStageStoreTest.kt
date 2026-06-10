/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.onboarding.store

import app.cash.turbine.test
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AppUserStageStoreTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val userStageDao: UserStageDao = mock()
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)
    private val testee = AppUserStageStore(userStageDao, coroutineRule.testDispatcherProvider, androidBrowserConfigFeature)

    @Test
    fun whenGetUserAppStageThenReturnCurrentStage() = runTest {
        givenCurrentStage(AppStage.DAX_ONBOARDING)

        val userAppStage = testee.getUserAppStage()

        assertEquals(AppStage.DAX_ONBOARDING, userAppStage)
    }

    @Test
    fun whenStageNewCompletedThenStageDaxOnboardingReturned() = runTest {
        givenCurrentStage(AppStage.NEW)

        val nextStage = testee.stageCompleted(AppStage.NEW)

        assertEquals(AppStage.DAX_ONBOARDING, nextStage)
    }

    @Test
    fun whenStageDaxOnboardingCompletedThenStageEstablishedReturned() = runTest {
        givenCurrentStage(AppStage.DAX_ONBOARDING)

        val nextStage = testee.stageCompleted(AppStage.DAX_ONBOARDING)

        assertEquals(AppStage.ESTABLISHED, nextStage)
    }

    @Test
    fun whenStageEstablishedCompletedThenStageEstablishedReturned() = runTest {
        givenCurrentStage(AppStage.ESTABLISHED)

        val nextStage = testee.stageCompleted(AppStage.ESTABLISHED)

        assertEquals(AppStage.ESTABLISHED, nextStage)
    }

    @Test
    fun whenMoveToStageThenUpdateUserStageInDao() = runTest {
        testee.moveToStage(AppStage.DAX_ONBOARDING)
        verify(userStageDao).updateUserStage(AppStage.DAX_ONBOARDING)
    }

    @Test
    fun `when stage observer attached, then return NEW stage by default`() = runTest {
        whenever(userStageDao.currentUserAppStageFlow()).thenReturn(flowOf(null))
        val expected = AppStage.NEW

        val actual = testee.userAppStageFlow().first()

        assertEquals(expected, actual)
    }

    @Test
    fun `when stage updated, then observer notified`() = runTest {
        val flow = MutableSharedFlow<UserStage>()
        whenever(userStageDao.currentUserAppStageFlow()).thenReturn(flow)
        val expected = AppStage.ESTABLISHED

        testee.userAppStageFlow().test {
            flow.emit(UserStage(appStage = expected))
            val actual = awaitItem()
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `when user is established and guard enabled then return established app stage without updating`() = runTest {
        givenCurrentStage(AppStage.ESTABLISHED)

        val result = testee.stageCompleted(AppStage.NEW)

        assertEquals(AppStage.ESTABLISHED, result)
        verify(userStageDao).currentUserAppStage()
    }

    @Test
    fun `when user is established and guard disabled then update app stage`() = runTest {
        givenCurrentStage(AppStage.ESTABLISHED)
        androidBrowserConfigFeature.establishedAppStageGuard().setRawStoredState(State(false))

        val result = testee.stageCompleted(AppStage.NEW)

        assertEquals(AppStage.DAX_ONBOARDING, result)
        verify(userStageDao).updateUserStage(AppStage.DAX_ONBOARDING)
    }

    @Test
    fun `when user is not established and guard enabled then update app stage`() = runTest {
        givenCurrentStage(AppStage.NEW)

        val result = testee.stageCompleted(AppStage.NEW)

        assertEquals(AppStage.DAX_ONBOARDING, result)
        verify(userStageDao).updateUserStage(AppStage.DAX_ONBOARDING)
    }

    @Test
    fun `when user is not established and guard disabled then update app stage`() = runTest {
        givenCurrentStage(AppStage.NEW)
        androidBrowserConfigFeature.establishedAppStageGuard().setRawStoredState(State(false))

        val result = testee.stageCompleted(AppStage.NEW)

        assertEquals(AppStage.DAX_ONBOARDING, result)
        verify(userStageDao).updateUserStage(AppStage.DAX_ONBOARDING)
    }

    private suspend fun givenCurrentStage(appStage: AppStage) {
        whenever(userStageDao.currentUserAppStage()).thenReturn(UserStage(appStage = appStage))
    }
}
