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

package com.duckduckgo.duckchat.impl.onboarding

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.store.DefaultTogglePosition
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Id
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@RunWith(AndroidJUnit4::class)
class OnboardingDuckAiNewTabTogglePositionDataPluginImplTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val duckChat: DuckChatInternal = mock()

    private val testee = OnboardingDuckAiNewTabTogglePositionDataPluginImpl(
        context = context,
        duckChat = duckChat,
    )

    @Test
    fun whenIdRequestedThenItIsTheNewTabTogglePositionChoice() {
        assertEquals(Id.DuckAiNewTabTogglePosition, testee.id)
    }

    /**
     * The ids are the pixel values for the step, and they match the ones the Duck.ai settings screen
     * reports, so they are pinned here rather than left to follow a rename.
     */
    @Test
    fun whenOptionsOfferedThenTheirIdsAreTheShippedOnes() = runTest {
        assertEquals(listOf("duckAI", "lastUsed"), testee.options().map { it.id })
    }

    @Test
    fun whenOptionsOfferedThenTheyCarryNoIcon() = runTest {
        testee.options().forEach { assertNull(it.iconRes) }
    }

    @Test
    fun whenTheFirstOptionIsAppliedThenNewTabsOpenWithDuckAi() = runTest {
        testee.apply(testee.options().first())

        verify(duckChat).setDefaultTogglePosition(DefaultTogglePosition.DUCK_AI)
    }

    @Test
    fun whenTheSecondOptionIsAppliedThenNewTabsFollowTheLastUsedPosition() = runTest {
        testee.apply(testee.options()[1])

        verify(duckChat).setDefaultTogglePosition(DefaultTogglePosition.LAST_USED)
    }

    @Test
    fun whenAnOptionFromAnotherPluginIsAppliedThenNothingIsWritten() = runTest {
        testee.apply(
            object : Option {
                override val id: String = "duckAI"
                override val label: String = "Open tabs with AI chat"
                override val iconRes: Int? = null
            },
        )

        verifyNoInteractions(duckChat)
    }
}
