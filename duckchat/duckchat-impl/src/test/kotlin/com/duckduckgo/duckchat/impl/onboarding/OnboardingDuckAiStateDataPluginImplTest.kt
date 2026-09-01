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
class OnboardingDuckAiStateDataPluginImplTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val duckChat: DuckChatInternal = mock()

    private val testee = OnboardingDuckAiStateDataPluginImpl(
        context = context,
        duckChat = duckChat,
    )

    @Test
    fun `when id requested then it is the duck ai state choice`() {
        assertEquals(Id.DuckAiState, testee.id)
    }

    /** The ids are the pixel values for the step, so they are pinned here rather than left to follow a rename. */
    @Test
    fun `when options offered then their ids are the shipped ones`() = runTest {
        assertEquals(listOf("duck_ai_on", "duck_ai_off"), testee.options().map { it.id })
    }

    @Test
    fun `when options offered then they carry no icon`() = runTest {
        testee.options().forEach { assertNull(it.iconRes) }
    }

    @Test
    fun `when the first option is applied then duck ai is turned on`() = runTest {
        testee.apply(testee.options().first())

        verify(duckChat).setEnableDuckChatUserSetting(true)
    }

    @Test
    fun `when the second option is applied then duck ai is left off`() = runTest {
        testee.apply(testee.options()[1])

        verify(duckChat).setEnableDuckChatUserSetting(false)
    }

    @Test
    fun `when an option from another plugin is applied then nothing is written`() = runTest {
        testee.apply(
            object : Option {
                override val id: String = "duck_ai_on"
                override val label: String = "Turn Duck.ai On"
                override val iconRes: Int? = null
            },
        )

        verifyNoInteractions(duckChat)
    }
}
