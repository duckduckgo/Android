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

package com.duckduckgo.autofill.ui.credential.management

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.AutofillDomainFormatterDomainNameOnly
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.ui.credential.management.AutofillManagementRecyclerAdapter.ListItem
import com.duckduckgo.autofill.ui.credential.management.AutofillManagementRecyclerAdapter.ListItem.Credential
import com.duckduckgo.autofill.ui.credential.management.AutofillManagementRecyclerAdapter.ListItem.GroupHeading
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialGrouperTest {

    private val initialExtractor: InitialExtractor = object : InitialExtractor {
        override fun extractInitial(loginCredentials: LoginCredentials): Char {
            val data = loginCredentials.domainTitle ?: loginCredentials.domain
            return data?.firstOrNull() ?: '#'
        }
    }

    private val domainFormatter = AutofillDomainFormatterDomainNameOnly()

    private val testee = CredentialGrouper(initialExtractor, CredentialListSorterByTitleAndDomain(domainFormatter))

    @Test
    fun whenEmptyListInThenEmptyListOut() {
        val credentials = emptyList<LoginCredentials>()
        val grouped = testee.group(credentials)
        assertTrue(grouped.isEmpty())
    }

    @Test
    fun whenSingleCredentialThenInitialAdded() {
        val credentials = listOf(
            creds("example.com")
        )
        val grouped = testee.group(credentials)

        grouped.assertTotalSize(expected = 2)
        grouped.assertNumberOfHeadings(expected = 1)
        grouped[0].assertIsGroupHeading('e')
        grouped[1].assertIsCredential("example.com")
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
        grouped[0].assertIsGroupHeading('e')
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
        grouped[0].assertIsGroupHeading('b')
        grouped[2].assertIsGroupHeading('e')
        grouped[5].assertIsGroupHeading('f')
    }

    private fun List<ListItem>.assertNumberOfHeadings(expected: Int) {
        assertEquals(expected, this.count { it is GroupHeading })
    }

    private fun List<ListItem>.assertTotalSize(expected: Int) {
        assertEquals(expected, this.size)
    }

    private fun ListItem.assertIsGroupHeading(expectedInitial: Char) {
        assertTrue(this is GroupHeading)
        assertEquals(expectedInitial, (this as GroupHeading).initial)
    }

    private fun ListItem.assertIsCredential(expectedDomain: String) {
        assertTrue(this is Credential)
        assertEquals(expectedDomain, (this as Credential).credentials.domain)
    }

    private fun creds(domain: String? = null, title: String? = null): LoginCredentials {
        return LoginCredentials(domain = domain, domainTitle = title, username = null, password = null)
    }

}
