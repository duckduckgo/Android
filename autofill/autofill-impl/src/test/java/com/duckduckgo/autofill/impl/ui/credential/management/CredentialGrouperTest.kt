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

package com.duckduckgo.autofill.impl.ui.credential.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.encoding.TestUrlUnicodeNormalizer
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.CredentialGrouper
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.CredentialInitialExtractor
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.CredentialListSorterByTitleAndDomain
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem.CredentialListItem.Credential
import com.duckduckgo.autofill.impl.ui.credential.management.viewing.list.ListItem.GroupHeading
import com.duckduckgo.autofill.impl.urlmatcher.AutofillDomainNameUrlMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialGrouperTest {

    private val autofillUrlMatcher = AutofillDomainNameUrlMatcher(TestUrlUnicodeNormalizer())
    private val initialExtractor = CredentialInitialExtractor(autofillUrlMatcher = autofillUrlMatcher)

    private val testee = CredentialGrouper(
        initialExtractor = initialExtractor,
        sorter = CredentialListSorterByTitleAndDomain(autofillUrlMatcher),
    )

    @Test
    fun whenEmptyListInThenEmptyListOut() {
        val credentials = emptyList<LoginCredentials>()
        val grouped = testee.group(credentials)
        assertTrue(grouped.isEmpty())
    }

    @Test
    fun whenSingleCredentialThenInitialAdded() {
        val credentials = listOf(
            creds("example.com"),
        )
        val grouped = testee.group(credentials)

        grouped.assertTotalSize(expected = 2)
        grouped.assertNumberOfHeadings(expected = 1)
        grouped[0].assertIsGroupHeading("E")
        grouped[1].assertIsCredentialWithDomain("example.com")
    }

    @Test
    fun whenMultipleCredentialsWithSameInitialThenOnlyOneGroupAdded() {
        val credentials = listOf(
            creds("example.com"),
            creds("energy.com"),
            creds("elastic.com"),
        )
        val grouped = testee.group(credentials)

        grouped.assertNumberOfHeadings(expected = 1)
        grouped.assertTotalSize(expected = 4)
        grouped[0].assertIsGroupHeading("E")
    }

    @Test
    fun whenCredentialsWithDifferentInitialsThenMultipleGroupsAdded() {
        val credentials = listOf(
            creds("example.com"),
            creds("foo.com"),
            creds("bar.com"),
            creds("energy.com"),
        )
        val grouped = testee.group(credentials)

        grouped.assertNumberOfHeadings(expected = 3)
        grouped.assertTotalSize(expected = 7)
        grouped[0].assertIsGroupHeading("B")
        grouped[2].assertIsGroupHeading("E")
        grouped[5].assertIsGroupHeading("F")
    }

    @Test
    fun whenCombinationOfDomainsAndTitlesThenGroupsTakenFromTitlesWhenTheyExist() {
        val credentials = listOf(
            creds(domain = "energy.com"),
            creds(domain = "amazon.com", title = "Smile Amazon"),
            creds(domain = "example.com", title = "C"),
            creds(domain = "amazon.com"),
            creds(domain = "bar.com"),
        )
        val grouped = testee.group(credentials)

        grouped.assertNumberOfHeadings(expected = 5)
        grouped.assertTotalSize(expected = 10)
        grouped[0].assertIsGroupHeading("A")
        grouped[2].assertIsGroupHeading("B")
        grouped[4].assertIsGroupHeading("C")
        grouped[6].assertIsGroupHeading("E")
        grouped[8].assertIsGroupHeading("S")
    }

    @Test
    fun whenListContainsAnEntryWithAMissingDomainAndTitleThenGroupedIntoPlaceholder() {
        val credentials = listOf(
            creds(domain = "amazon.com", title = "Smile Amazon"),
            creds(domain = "example.com"),
            creds(domain = null, title = null),
            creds(domain = "null", title = "Title"),
        )
        val grouped = testee.group(credentials)

        grouped.assertNumberOfHeadings(expected = 4)
        grouped.assertTotalSize(expected = 8)
        grouped[0].assertIsGroupHeading("#")
        grouped[1].assertIsCredentialWithDomain(expectedDomain = null)
        grouped[2].assertIsGroupHeading("E")
        grouped[4].assertIsGroupHeading("S")
        grouped[6].assertIsGroupHeading("T")
    }

    @Test
    fun whenCharactersCanBeDeconstructedThenTheyDoNotGetTheirOwnGroup() {
        val credentials = listOf(
            creds(title = "ä"),
            creds(title = "A"),
            creds(title = "Ğ"),
            creds(title = "G"),

        )
        val grouped = testee.group(credentials)

        val expectedNumberOfHeadings = 2
        grouped.assertNumberOfHeadings(expected = expectedNumberOfHeadings)
        grouped.assertTotalSize(expected = expectedNumberOfHeadings + credentials.size)
        grouped[0].assertIsGroupHeading("A")
        grouped[3].assertIsGroupHeading("G")
    }

    @Test
    fun whenNonEnglishAlphabetCharactersThenTheyDoGetTheirOwnGroup() {
        val credentials = listOf(
            creds(title = "ß"),
        )
        val grouped = testee.group(credentials)

        val expectedNumberOfHeadings = 1
        grouped.assertNumberOfHeadings(expected = expectedNumberOfHeadings)
        grouped.assertTotalSize(expected = expectedNumberOfHeadings + credentials.size)
        grouped[0].assertIsGroupHeading("ß")
    }

    @Test
    fun whenEmojiThenTheTheyAreInPlaceholder() {
        val credentials = listOf(
            creds(title = "😅"),
        )
        val grouped = testee.group(credentials)

        val expectedNumberOfHeadings = 1
        grouped.assertNumberOfHeadings(expected = expectedNumberOfHeadings)
        grouped.assertTotalSize(expected = expectedNumberOfHeadings + credentials.size)
        grouped[0].assertIsGroupHeading("#")
    }

    @Test
    fun whenNumberThenGroupedIntoPlaceholder() {
        val credentials = listOf(
            creds(title = "8"),
            creds(title = "5"),
        )
        val grouped = testee.group(credentials)

        val expectedNumberOfHeadings = 1
        grouped.assertNumberOfHeadings(expected = expectedNumberOfHeadings)
        grouped.assertTotalSize(expected = expectedNumberOfHeadings + credentials.size)
        grouped[0].assertIsGroupHeading("#")
        grouped[1].assertIsCredentialWithTitle("5")
        grouped[2].assertIsCredentialWithTitle("8")
    }

    @Test
    fun whenListMixtureOfAccentedCharactersThenAccentedCharactersDoNotGetTheirOwnGroups() {
        val credentials = listOf(
            creds(title = "A"),
            creds(title = "Ab"),
            creds(title = "ab"),
            creds(title = "B"),
            creds(title = "Bc"),
            creds(title = "bc"),
            creds(title = "C"),
            creds(title = "Ca"),
            creds(title = "ca"),
            creds(title = "Ç"),
            creds(title = "Ça"),
            creds(title = "ça"),
            creds(title = "D"),
        )
        val grouped = testee.group(credentials)

        val expectedNumberOfHeadings = 4
        grouped.assertNumberOfHeadings(expected = expectedNumberOfHeadings)
        grouped.assertTotalSize(expected = expectedNumberOfHeadings + credentials.size)
        grouped[0].assertIsGroupHeading("A")
        grouped[1].assertIsCredentialWithTitle("A")
        grouped[2].assertIsCredentialWithTitle("Ab")
        grouped[3].assertIsCredentialWithTitle("ab")
        grouped[4].assertIsGroupHeading("B")
        grouped[5].assertIsCredentialWithTitle("B")
        grouped[6].assertIsCredentialWithTitle("Bc")
        grouped[7].assertIsCredentialWithTitle("bc")
        grouped[8].assertIsGroupHeading("C")
        grouped[9].assertIsCredentialWithTitle("C")
        grouped[10].assertIsCredentialWithTitle("Ç")
        grouped[11].assertIsCredentialWithTitle("Ca")
        grouped[12].assertIsCredentialWithTitle("ca")
        grouped[13].assertIsCredentialWithTitle("Ça")
        grouped[14].assertIsCredentialWithTitle("ça")
        grouped[15].assertIsGroupHeading("D")
        grouped[16].assertIsCredentialWithTitle("D")
    }

    private fun List<ListItem>.assertNumberOfHeadings(expected: Int) {
        assertEquals(expected, this.count { it is GroupHeading })
    }

    private fun List<ListItem>.assertTotalSize(expected: Int) {
        assertEquals(expected, this.size)
    }

    private fun ListItem.assertIsGroupHeading(expectedInitial: String) {
        assertTrue(this is GroupHeading)
        assertEquals(expectedInitial, (this as GroupHeading).label)
    }

    private fun ListItem.assertIsCredentialWithDomain(expectedDomain: String?) {
        assertTrue(this is Credential)
        assertEquals(expectedDomain, (this as Credential).credentials.domain)
    }

    private fun ListItem.assertIsCredentialWithTitle(expectedTitle: String?) {
        assertTrue(this is Credential)
        assertEquals(expectedTitle, (this as Credential).credentials.domainTitle)
    }

    private fun creds(domain: String? = null, title: String? = null): LoginCredentials {
        return LoginCredentials(domain = domain, domainTitle = title, username = null, password = null)
    }
}
