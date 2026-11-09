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

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealNewChatHintsRendererTest {
    @get:Rule val coroutineRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(DuckChatFeature::class.java)
    private val testee = RealNewChatHintsRenderer(feature, coroutineRule.testDispatcherProvider)

    @Test
    fun whenFlagIsDisabledThenNoViewIsCreated() = runTest {
        feature.newChatHints().setRawStoredState(Toggle.State(enable = false))

        assertNull(testee.create(ApplicationProvider.getApplicationContext(), "tab"))
    }

    @Test
    fun whenFlagIsEnabledThenTheViewIsCreatedForTheTab() = runTest {
        feature.newChatHints().setRawStoredState(Toggle.State(enable = true))

        val view = testee.create(ApplicationProvider.getApplicationContext(), "tab") as NewChatHintsView

        assertEquals("tab", view.tabId)
    }
}
