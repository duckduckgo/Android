/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.fire.promo

import com.duckduckgo.app.fire.store.FireDataStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealFireTabsPromosTest {

    @get:Rule val coroutineRule = CoroutineTestRule()

    private val fireDataStore: FireDataStore = mock()
    private val remoteMessageModel: RemoteMessageModel = mock()

    private val testee by lazy {
        RealFireTabsPromos(
            fireDataStore = fireDataStore,
            remoteMessageModel = remoteMessageModel,
            dispatchers = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenTabSwitcherPromoNotDismissedThenCanShowTabSwitcherPromoTrue() = runTest {
        whenever(fireDataStore.isTabSwitcherPromoDismissed()).thenReturn(false)
        assertTrue(testee.canShowTabSwitcherPromo())
    }

    @Test
    fun whenTabSwitcherPromoDismissedThenCanShowTabSwitcherPromoFalse() = runTest {
        whenever(fireDataStore.isTabSwitcherPromoDismissed()).thenReturn(true)
        assertFalse(testee.canShowTabSwitcherPromo())
    }

    @Test
    fun whenOnTabSwitcherPromoShownThenTabSwitcherDismissed() = runTest {
        testee.onTabSwitcherPromoShown()
        verify(fireDataStore).setTabSwitcherPromoDismissed(true)
    }

    @Test
    fun whenOnFireModeEnteredThenUsedFireModeAndTabSwitcherDismissedRecorded() = runTest {
        testee.onFireModeEntered()
        verify(fireDataStore).setUsedFireMode(true)
        verify(fireDataStore).setTabSwitcherPromoDismissed(true)
    }

    @Test
    fun whenOnFireModeEnteredAndFireTabsMessageActiveThenMessageDismissed() = runTest {
        val message = fireTabsRemoteMessage()
        whenever(remoteMessageModel.getActiveMessage()).thenReturn(message)

        testee.onFireModeEntered()

        verify(remoteMessageModel).onMessageDismissed(message)
    }

    @Test
    fun whenOnFireModeEnteredAndNoActiveMessageThenNoMessageDismissed() = runTest {
        whenever(remoteMessageModel.getActiveMessage()).thenReturn(null)

        testee.onFireModeEntered()

        verify(remoteMessageModel, never()).onMessageDismissed(any())
    }

    private fun fireTabsRemoteMessage() = RemoteMessage(
        id = "fire_tabs_promo",
        content = Content.BigTwoActions(
            titleText = "",
            descriptionText = "",
            placeholder = Content.Placeholder.FIRE_TABS,
            primaryActionText = "",
            primaryAction = Action.FireTabsPromo,
            secondaryActionText = "",
            secondaryAction = Action.Dismiss,
        ),
        matchingRules = emptyList(),
        exclusionRules = emptyList(),
        surfaces = emptyList(),
    )
}
