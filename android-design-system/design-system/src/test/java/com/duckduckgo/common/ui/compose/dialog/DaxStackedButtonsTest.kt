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

package com.duckduckgo.common.ui.compose.dialog

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DaxStackedButtonsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun primaryStackedButtonPillsAreTwelveDpApart() {
        show { DaxPrimaryStackedButtons(it) }

        assertVisibleGaps(12f)
    }

    @Test
    fun destructiveStackedButtonPillsAreTwelveDpApart() {
        show { DaxDestructiveStackedButtons(it) }

        assertVisibleGaps(12f)
    }

    private fun show(stack: @Composable (ImmutableList<DaxStackedButton>) -> Unit) {
        compose.setContent {
            DuckDuckGoTheme(isDarkTheme = false) {
                stack(persistentListOf(*Labels.map { DaxStackedButton(text = it, onClick = {}) }.toTypedArray()))
            }
        }
    }

    private fun assertVisibleGaps(expected: Float) {
        Labels.zipWithNext { above, below ->
            val gap = compose.onNodeWithText(below).getUnclippedBoundsInRoot().top - compose.onNodeWithText(above).getUnclippedBoundsInRoot().bottom
            assertEquals("$above to $below", expected, gap.value, 0.5f)
        }
    }

    private companion object {
        val Labels = listOf("First", "Second", "Third")
    }
}
