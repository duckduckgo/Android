/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.duckchat.api.DuckChatSettingsNoParams
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RealDuckChatTest {

    @get:org.junit.Rule
    var coroutineRule = com.duckduckgo.common.test.CoroutineTestRule()

    private val mockDuckPlayerFeatureRepository: DuckChatFeatureRepository =
        mock()
    private val duckChatFeature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val moshi: Moshi = Moshi.Builder().build()
    private val dispatcherProvider = coroutineRule.testDispatcherProvider
    private val mockGlobalActivityStarter: GlobalActivityStarter = mock()
    private val mockContext: Context = mock()
    private val mockPixel: Pixel = mock()
    private val mockIntent: Intent = mock()

    private val testee = spy(
        RealDuckChat(
            mockDuckPlayerFeatureRepository,
            duckChatFeature,
            moshi,
            dispatcherProvider,
            mockGlobalActivityStarter,
            mockContext,
            true,
            coroutineRule.testScope,
            mockPixel,
        ),
    )

    @Before
    fun setup() = runTest {
        whenever(mockDuckPlayerFeatureRepository.shouldShowInBrowserMenu()).thenReturn(true)
        whenever(mockContext.getString(any())).thenReturn("Duck.ai")
        setFeatureToggle(true)
        whenever(mockGlobalActivityStarter.startIntent(any(), any<DuckChatWebViewActivityWithParams>())).thenReturn(mockIntent)
    }

    @Test
    fun whenSetShowInBrowserMenuSetTrue_thenPixelOnIsSent() = runTest {
        testee.setShowInBrowserMenuUserSetting(true)
        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_MENU_SETTING_ON)
    }

    @Test
    fun whenSetShowInBrowserMenuSetFalse_thenPixelOffIsSent() = runTest {
        testee.setShowInBrowserMenuUserSetting(false)
        verify(mockPixel).fire(DuckChatPixelName.DUCK_CHAT_MENU_SETTING_OFF)
    }

    @Test
    fun whenDuckChatIsEnabled_isEnabledReturnsTrue() = runTest {
        val result = testee.isEnabled()
        assertTrue(result)
    }

    @Test
    fun whenDuckChatIsDisabled_isEnabledReturnsFalse() = runTest {
        setFeatureToggle(false)

        val result = testee.isEnabled()
        assertFalse(result)
    }

    @Test
    fun observeShowInBrowserMenuUserSetting_emitsCorrectValues() = runTest {
        whenever(mockDuckPlayerFeatureRepository.observeShowInBrowserMenu()).thenReturn(flowOf(true, false))

        val results = testee.observeShowInBrowserMenuUserSetting().take(2).toList()
        assertTrue(results[0])
        assertFalse(results[1])
    }

    @Test
    fun whenFeatureEnabled_showInBrowserMenuReturnsValueFromRepository() {
        val result = testee.showInBrowserMenu()
        assertTrue(result)
    }

    @Test
    fun whenFeatureDisabled_showInBrowserMenuReturnsFalse() {
        setFeatureToggle(false)

        val result = testee.showInBrowserMenu()
        assertFalse(result)
    }

    @Test
    fun whenOpenDuckChatCalled_activityStarted() = runTest {
        testee.openDuckChat()
        verify(mockGlobalActivityStarter).startIntent(
            mockContext,
            DuckChatWebViewActivityWithParams(
                url = "https://duckduckgo.com/?q=DuckDuckGo+AI+Chat&ia=chat&duckai=5",
            ),
        )
        verify(mockContext).startActivity(any())
        verify(mockDuckPlayerFeatureRepository).registerOpened()
    }

    @Test
    fun whenOpenDuckChatCalledWithQuery_activityStartedWithQuery() = runTest {
        testee.openDuckChat(query = "example")
        verify(mockGlobalActivityStarter).startIntent(
            mockContext,
            DuckChatWebViewActivityWithParams(
                url = "https://duckduckgo.com/?q=example&ia=chat&duckai=5",
            ),
        )
        verify(mockContext).startActivity(any())
        verify(mockDuckPlayerFeatureRepository).registerOpened()
    }

    @Test
    fun whenOpenDuckChatCalledWithQueryAndAutoPrompt_activityStartedWithQueryAndAutoPrompt() = runTest {
        testee.openDuckChatWithAutoPrompt(query = "example")
        verify(mockGlobalActivityStarter).startIntent(
            mockContext,
            DuckChatWebViewActivityWithParams(
                url = "https://duckduckgo.com/?q=example&prompt=1&ia=chat&duckai=5",
            ),
        )
        verify(mockContext).startActivity(any())
        verify(mockDuckPlayerFeatureRepository).registerOpened()
    }

    @Test
    fun whenIsDuckDuckGoHostAndDuckChatEnabledAndIsDuckChatLink_isDuckChatUrl() {
        assertTrue(testee.isDuckChatUrl("https://duckduckgo.com/?ia=chat".toUri()))
    }

    @Test
    fun whenIsDuckDuckGoHostAndDuckChatEnabledAndIsNotDuckChatLink_isNotDuckChatUrl() {
        assertFalse(testee.isDuckChatUrl("https://duckduckgo.com/?q=test".toUri()))
    }

    @Test
    fun whenIsNotDuckDuckGoHostAndDuckChatEnabled_isNotDuckChatUrl() {
        assertFalse(testee.isDuckChatUrl("https://example.com/?ia=chat".toUri()))
    }

    @Test
    fun `when was opened before queried, then repo state is returned`() = runTest {
        whenever(mockDuckPlayerFeatureRepository.wasOpenedBefore()).thenReturn(true)

        assertTrue(testee.wasOpenedBefore())
    }

    @Test
    fun whenOpenDuckChatSettingsCalledThenGlobalActivityStarterCalledWithDuckChatSettings() = runTest {
        whenever(mockGlobalActivityStarter.startIntent(any(), any<ActivityParams>())).thenReturn(Intent())

        testee.openDuckChatSettings()

        verify(mockGlobalActivityStarter).startIntent(mockContext, DuckChatSettingsNoParams)

        val intentCaptor = argumentCaptor<Intent>()
        verify(mockContext).startActivity(intentCaptor.capture())
        val capturedIntent = intentCaptor.firstValue

        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, capturedIntent.flags)

        verify(testee).closeDuckChat()
    }

    @Test
    fun whenCloseDuckChatCalledThenOnCloseIsInvoked() = runTest {
        val testLifecycleOwner = TestLifecycleOwner(initialState = CREATED)

        var onCloseCalled = false
        testee.observeCloseEvent(testLifecycleOwner) {
            onCloseCalled = true
        }

        testLifecycleOwner.currentState = Lifecycle.State.STARTED

        testee.closeDuckChat()

        advanceUntilIdle()

        assertTrue(onCloseCalled)
    }

    private fun setFeatureToggle(enabled: Boolean) {
        duckChatFeature.self().setRawStoredState(State(enabled))
        testee.onPrivacyConfigDownloaded()
    }
}
