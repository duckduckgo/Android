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

package com.duckduckgo.autofill.impl.ui.credential.selecting

import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ButtonType
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ButtonType.*
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ButtonTypeDecider
import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonTypeDeciderTest {

    private val testee = ButtonTypeDecider()

    @Test
    fun whenPositionIs0ThenButtonTypeIsAlwaysPrimary() {
        testee.determineButtonType(listPosition = 0, fullListSize = singleItem(), expandedMode = false).assertIsPrimaryButton()
        testee.determineButtonType(listPosition = 0, fullListSize = singleItem(), expandedMode = true).assertIsPrimaryButton()
        testee.determineButtonType(listPosition = 0, fullListSize = largeList(), expandedMode = false).assertIsPrimaryButton()
        testee.determineButtonType(listPosition = 0, fullListSize = largeList(), expandedMode = true).assertIsPrimaryButton()
        testee.determineButtonType(listPosition = 0, fullListSize = 3, expandedMode = false).assertIsPrimaryButton()
        testee.determineButtonType(listPosition = 0, fullListSize = 3, expandedMode = true).assertIsPrimaryButton()
    }

    @Test
    fun whenPositionIs1ThenButtonTypeIsAlwaysSecondary() {
        testee.determineButtonType(listPosition = 1, fullListSize = singleItem(), expandedMode = false).assertIsSecondaryButton()
        testee.determineButtonType(listPosition = 1, fullListSize = singleItem(), expandedMode = true).assertIsSecondaryButton()
        testee.determineButtonType(listPosition = 1, fullListSize = largeList(), expandedMode = false).assertIsSecondaryButton()
        testee.determineButtonType(listPosition = 1, fullListSize = largeList(), expandedMode = true).assertIsSecondaryButton()
        testee.determineButtonType(listPosition = 1, fullListSize = 3, expandedMode = false).assertIsSecondaryButton()
        testee.determineButtonType(listPosition = 1, fullListSize = 3, expandedMode = true).assertIsSecondaryButton()
    }

    @Test
    fun whenPositionIs2AndFullSizeListIsExactly3ThenButtonTypeIsAlwaysSecondary() {
        testee.determineButtonType(listPosition = 2, fullListSize = 3, expandedMode = false).assertIsSecondaryButton()
        testee.determineButtonType(listPosition = 2, fullListSize = 3, expandedMode = true).assertIsSecondaryButton()
    }

    @Test
    fun whenPositionIs2AndFullSizeListIsMoreAndNotExpandedViewThenButtonTypeIsShowMore() {
        testee.determineButtonType(listPosition = 2, fullListSize = largeList(), expandedMode = false).assertIsShowMoreOptionsButton()
    }

    @Test
    fun whenPositionIs2AndFullSizeListIsMoreAndExpandedViewThenButtonTypeIsSecondary() {
        testee.determineButtonType(listPosition = 2, fullListSize = largeList(), expandedMode = true).assertIsSecondaryButton()
    }

    @Test
    fun whenPositionIsGreaterThan2ThenButtonTypeIsAlwaysSecondary() {
        testee.determineButtonType(listPosition = 3, fullListSize = largeList(), expandedMode = false).assertIsSecondaryButton()
        testee.determineButtonType(listPosition = 3, fullListSize = largeList(), expandedMode = true).assertIsSecondaryButton()
        testee.determineButtonType(listPosition = 3, fullListSize = 4, expandedMode = false).assertIsSecondaryButton()
        testee.determineButtonType(listPosition = 3, fullListSize = 4, expandedMode = true).assertIsSecondaryButton()
    }

    private fun singleItem() = 1
    private fun largeList() = 100
}

private fun ButtonType.assertIsPrimaryButton() {
    assertTrue("Expected primary button but was ${this.javaClass.simpleName}", this is UseCredentialPrimaryButton)
}

private fun ButtonType.assertIsSecondaryButton() {
    assertTrue("Expected secondary button but was ${this.javaClass.simpleName}", this is UseCredentialSecondaryButton)
}

private fun ButtonType.assertIsShowMoreOptionsButton() {
    assertTrue("Expected show more options button but was ${this.javaClass.simpleName}", this is ShowMoreButton)
}
