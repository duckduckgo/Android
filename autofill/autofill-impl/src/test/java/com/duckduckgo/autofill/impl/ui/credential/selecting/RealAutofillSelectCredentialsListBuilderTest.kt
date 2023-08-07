/*
 * Copyright (c) 2023 DuckDuckGo
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.ui.credential.selecting.AutofillSelectCredentialsGrouper.Groups
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ListItem
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ListItem.GroupHeading
import com.duckduckgo.autofill.impl.ui.credential.selecting.CredentialsPickerRecyclerAdapter.ListItem.VerticalSpacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealAutofillSelectCredentialsListBuilderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testee = RealAutofillSelectCredentialsListBuilder(context)

    @Test
    fun whenEmptyInputThenEmptyListOut() {
        val sortedGroup = Groups(
            perfectMatches = listOf(),
            partialMatches = mapOf(),
            shareableCredentials = emptyMap(),
        )
        assertTrue(testee.buildFlatList(sortedGroup).isEmpty())
    }

    @Test
    fun whenPerfectMatchAndNoPartialMatchThenFromThisWebsiteLabelOmitted() {
        val sortedGroup = Groups(
            perfectMatches = listOf(creds()),
            partialMatches = mapOf(),
            shareableCredentials = emptyMap(),
        )
        val list = testee.buildFlatList(sortedGroup)
        list[0].assertIsVerticalSpacing()
    }

    @Test
    fun whenOnePerfectMatchThenButtonIsPrimaryType() {
        val sortedGroup = Groups(
            perfectMatches = listOf(creds()),
            partialMatches = mapOf(),
            shareableCredentials = emptyMap(),
        )
        val list = testee.buildFlatList(sortedGroup)
        list[1].assertIsPrimaryButton()
    }

    @Test
    fun whenMultiplePerfectMatchesThenFirstButtonOnlyIsPrimaryType() {
        val sortedGroup = Groups(
            perfectMatches = listOf(creds(), creds(), creds()),
            partialMatches = mapOf(),
            shareableCredentials = emptyMap(),
        )
        val list = testee.buildFlatList(sortedGroup)
        list[1].assertIsPrimaryButton()
        list[2].assertIsSecondaryButton()
        list[3].assertIsSecondaryButton()
    }

    @Test
    fun whenNoPerfectMatchesAndOnePartialMatchThenGroupHeaderAdded() {
        val sortedGroup = Groups(
            perfectMatches = emptyList(),
            partialMatches = mapOf(
                Pair("foo.example.com", listOf(creds())),
            ),
            shareableCredentials = emptyMap(),
        )
        val list = testee.buildFlatList(sortedGroup)
        list[0].assertIsDomainGroupHeading("foo.example.com")
    }

    @Test
    fun whenNoPerfectMatchesAndOnePartialMatchThenPartialMatchAddedAsPrimaryButton() {
        val sortedGroup = Groups(
            perfectMatches = emptyList(),
            partialMatches = mapOf(
                Pair("foo.example.com", listOf(creds())),
            ),
            shareableCredentials = emptyMap(),
        )
        val list = testee.buildFlatList(sortedGroup)
        list[1].assertIsPrimaryButton()
    }

    @Test
    fun whenNoPerfectMatchesAndTwoPartialMatchesInSameDomainThenFirstPartialMatchAddedAsPrimaryButton() {
        val sortedGroup = Groups(
            perfectMatches = emptyList(),
            partialMatches = mapOf(
                Pair("foo.example.com", listOf(creds(), creds())),
            ),
            shareableCredentials = emptyMap(),
        )
        val list = testee.buildFlatList(sortedGroup)
        list[1].assertIsPrimaryButton()
        list[2].assertIsSecondaryButton()
    }

    @Test
    fun whenPerfectMatchesAndPartialMatchesThenFromThisWebsiteLabelShown() {
        val sortedGroup = Groups(
            perfectMatches = listOf(creds()),
            partialMatches = mapOf(
                Pair("foo.example.com", listOf(creds())),
                Pair("bar.example.com", listOf(creds())),
            ),
            shareableCredentials = emptyMap(),
        )
        val list = testee.buildFlatList(sortedGroup)
        list[0].assertIsFromThisWebsiteGroupHeading()
    }

    @Test
    fun whenPerfectMatchesAndOnePartialMatchThenPartialMatchAddedAsSecondaryButton() {
        val sortedGroup = Groups(
            perfectMatches = listOf(creds()),
            partialMatches = mapOf(
                Pair("foo.example.com", listOf(creds())),
            ),
            shareableCredentials = emptyMap(),
        )
        val list = testee.buildFlatList(sortedGroup)
        list[3].assertIsSecondaryButton()
    }

    @Test
    fun whenNoPerfectMatchesAndMultiplePartialMatchesAcrossSitesThenFirstPartialMatchAddedAsPrimaryButton() {
        val sortedGroup = Groups(
            perfectMatches = emptyList(),
            partialMatches = mapOf(
                Pair("foo.example.com", listOf(creds(), creds())),
                Pair("bar.example.com", listOf(creds(), creds())),
            ),
            shareableCredentials = emptyMap(),
        )
        val list = testee.buildFlatList(sortedGroup)
        list[1].assertIsPrimaryButton()
        list[2].assertIsSecondaryButton()
        list[4].assertIsSecondaryButton()
        list[5].assertIsSecondaryButton()
    }

    @Test
    fun whenPerfectMatchesAndMultiplePartialMatchesThenListOutputAsExpected() {
        val sortedGroup = Groups(
            perfectMatches = listOf(creds(), creds()),
            partialMatches = mapOf(
                Pair("bar.example.com", listOf(creds())),
                Pair("foo.example.com", listOf(creds())),
                Pair("foo.example.com", listOf(creds())),
            ),
            shareableCredentials = emptyMap(),
        )
        val list = testee.buildFlatList(sortedGroup)
        list[0].assertIsFromThisWebsiteGroupHeading()
        list[1].assertIsPrimaryButton()
        list[2].assertIsSecondaryButton()
        list[3].assertIsDomainGroupHeading("bar.example.com")
        list[4].assertIsSecondaryButton()
        list[5].assertIsDomainGroupHeading("foo.example.com")
        list[6].assertIsSecondaryButton()
        list[6].assertIsSecondaryButton()
    }

    private fun ListItem.assertIsVerticalSpacing() = assertTrue("Not vertical spacing; is ${this.javaClass.simpleName}", this is VerticalSpacing)
    private fun ListItem.assertIsPrimaryButton() = assertTrue(
        "Not primary button; is ${this.javaClass.simpleName}",
        this is ListItem.CredentialPrimaryType,
    )
    private fun ListItem.assertIsSecondaryButton() = assertTrue(
        "Not secondary button; is ${this.javaClass.simpleName}",
        this is ListItem.CredentialSecondaryType,
    )
    private fun ListItem.assertIsDomainGroupHeading(name: String) {
        assertTrue("Not a group heading; is ${this.javaClass.simpleName}", this is GroupHeading)
        assertEquals(String.format("From %s", name), (this as GroupHeading).label)
    }
    private fun ListItem.assertIsFromThisWebsiteGroupHeading() {
        assertTrue("Not a group heading; is ${this.javaClass.simpleName}", this is GroupHeading)
        assertEquals("From This Website", (this as GroupHeading).label)
    }

    private fun creds(): LoginCredentials {
        return LoginCredentials(domain = null, username = null, password = null)
    }
}
