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

package com.duckduckgo.app.onboarding.ui.page.welcome

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialog
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogState
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [28], qualifiers = "w480dp-h1040dp-mdpi")
class OnboardingDialogContentTest {
    @get:Rule val compose = createComposeRule()
    private val store = ContentValueStore()
    private val dialog = DaxOnboardingDialogState()

    @Test
    fun welcomeShowsBothParagraphsAndRemovesOptionalParagraphOnReplacement() {
        val content =
            mutableStateOf(session(ContentConfig.Welcome(TextConfig.Literal("Welcome"), TextConfig.Literal("First"), TextConfig.Literal("Second"))))
        compose.setContent {
            DuckDuckGoTheme {
                OnboardingTheme {
                    val current = content.value
                    DaxOnboardingDialog(contentKey = current.config.toString(), title = "Welcome", animate = false, content = {
                        onContentReady(current::ready)
                        OnboardingDialogContent(current, enabled)
                    })
                }
            }
        }
        compose.onNodeWithText("First").assertIsDisplayed()
        compose.onNodeWithText("Second").assertIsDisplayed()
        compose.runOnIdle { content.value = session(ContentConfig.Welcome(TextConfig.Literal("Welcome"), TextConfig.Literal("Only"), null)) }
        compose.onNodeWithText("Only").assertIsDisplayed()
        compose.onAllNodesWithText("Second").assertCountEquals(0)
    }

    @Test
    fun comparisonRendersEveryConfiguredRowAndReadinessAfterSkip() {
        val config = ComparisonChartConfig.Ai
        val state = session(ContentConfig.ComparisonChart(TextConfig.Literal("Comparison"), config))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            DuckDuckGoTheme {
                OnboardingTheme {
                    DaxOnboardingDialog(contentKey = "comparison", title = "Comparison", state = dialog, animate = true, content = {
                        onContentReady(state::ready)
                        OnboardingDialogContent(state, enabled)
                    })
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle {
            dialog.skip()
            state.skip()
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(false, state.readyAnimated) }
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        config.rows.forEach { compose.onNodeWithText(context.getString(it.textRes)).assertIsDisplayed() }
    }

    private fun session(config: ContentConfig) = OnboardingContentState("test", config, store, {}, true)
}
