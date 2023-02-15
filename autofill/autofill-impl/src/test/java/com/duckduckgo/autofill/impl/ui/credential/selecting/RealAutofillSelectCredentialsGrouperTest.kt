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
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.CredentialListSorterByTitleAndDomain
import com.duckduckgo.autofill.impl.ui.credential.selecting.AutofillSelectCredentialsGrouper.Groups
import com.duckduckgo.autofill.store.urlmatcher.AutofillDomainNameUrlMatcher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealAutofillSelectCredentialsGrouperTest {

    private val urlMatcher = AutofillDomainNameUrlMatcher()
    private val sorter = CredentialListSorterByTitleAndDomain(urlMatcher)

    private val testee = RealAutofillSelectCredentialsGrouper(
        autofillUrlMatcher = urlMatcher,
        sorter = sorter,
    )

    @Test
    fun whenOnlyOnePerfectMatchThenCorrectlyGroupedIntoPerfectMatch() {
        val creds = listOf(creds("example.com"))
        val grouped = testee.group("example.com", unsortedCredentials = creds)
        grouped.assertNoPartialMatches()
        grouped.assertNumberOfPerfectMatches(1)
    }

    @Test
    fun whenMultiplePerfectMatchesThenAllCorrectlyGroupedIntoPerfectMatch() {
        val creds = listOf(creds("example.com"), creds("example.com"))
        val grouped = testee.group("example.com", unsortedCredentials = creds)
        grouped.assertNoPartialMatches()
        grouped.assertNumberOfPerfectMatches(2)
    }

    @Test
    fun whenNotAMatchThenNotIncludedInGroups() {
        val creds = listOf(creds("differentDomain.com"))
        val grouped = testee.group("example.com", unsortedCredentials = creds)
        grouped.assertNoPartialMatches()
        grouped.assertNoPerfectMatches()
    }

    @Test
    fun whenSinglePartialMatchThenGetsItsOwnGroup() {
        val creds = listOf(creds("foo.example.com"))
        val grouped = testee.group("example.com", unsortedCredentials = creds)
        grouped.assertNoPerfectMatches()
        grouped.assertNumberOfPartialMatchGroups(1)
        grouped.assertPositionInGroups(0, "foo.example.com")
        grouped.assertGroupSize(1, "foo.example.com")
    }

    @Test
    fun whenMultiplePartialMatchesWithSameSubdomainThenAllShareAGroup() {
        val creds = listOf(creds("foo.example.com"), creds("foo.example.com"))
        val grouped = testee.group("example.com", unsortedCredentials = creds)
        grouped.assertNoPerfectMatches()
        grouped.assertNumberOfPartialMatchGroups(1)
        grouped.assertPositionInGroups(0, "foo.example.com")
        grouped.assertGroupSize(2, "foo.example.com")
    }

    @Test
    fun whenMultipleDifferentPartialMatchesThenEachGetsTheirOwnGroup() {
        val creds = listOf(creds("foo.example.com"), creds("bar.example.com"), creds("bar.example.com"))
        val grouped = testee.group("example.com", unsortedCredentials = creds)
        grouped.assertNoPerfectMatches()
        grouped.assertNumberOfPartialMatchGroups(2)

        "bar.example.com".let {
            grouped.assertPositionInGroups(0, it)
            grouped.assertGroupSize(2, it)
        }

        "foo.example.com".let {
            grouped.assertPositionInGroups(1, it)
            grouped.assertGroupSize(1, it)
        }
    }

    @Test
    fun whenSortingPerfectMatchesThenLastEditedSortedFirst() {
        val creds = listOf(
            creds(lastUpdated = 100, domain = "example.com"),
            creds(lastUpdated = 300, domain = "example.com"),
            creds(lastUpdated = 200, domain = "example.com"),
        )
        val grouped = testee.group("example.com", unsortedCredentials = creds)
        assertEquals(300L, grouped.perfectMatches[0].lastUpdatedMillis)
        assertEquals(200L, grouped.perfectMatches[1].lastUpdatedMillis)
        assertEquals(100L, grouped.perfectMatches[2].lastUpdatedMillis)
    }

    @Test
    fun whenSortingPartialMatchesThenLastEditedSortedFirst() {
        val creds = listOf(
            creds(lastUpdated = 100, domain = "foo.example.com"),
            creds(lastUpdated = 300, domain = "foo.example.com"),
            creds(lastUpdated = 200, domain = "foo.example.com"),
        )
        val grouped = testee.group("example.com", unsortedCredentials = creds)
        val group = grouped.partialMatches["foo.example.com"]
        assertEquals(300L, group?.get(0)?.lastUpdatedMillis)
        assertEquals(200L, group?.get(1)?.lastUpdatedMillis)
        assertEquals(100L, group?.get(2)?.lastUpdatedMillis)
    }

    private fun Groups.assertNumberOfPartialMatchGroups(expectedSize: Int) {
        assertEquals(expectedSize, partialMatches.size)
    }

    private fun Groups.assertNoPerfectMatches() {
        assertTrue(perfectMatches.isEmpty())
    }

    private fun Groups.assertNoPartialMatches() {
        assertTrue(partialMatches.isEmpty())
    }

    private fun Groups.assertGroupSize(
        expectedSize: Int,
        groupName: String,
    ) {
        assertEquals(expectedSize, partialMatches[groupName]!!.size)
    }

    private fun Groups.assertPositionInGroups(
        expectedPosition: Int,
        groupName: String,
    ) {
        partialMatches.keys.forEachIndexed { index, key ->
            if (key == groupName) {
                assertEquals(expectedPosition, index)
                return
            }
        }
        fail("Group [$groupName] not found")
    }

    private fun Groups.assertNumberOfPerfectMatches(expectedSize: Int) {
        assertEquals(expectedSize, perfectMatches.size)
    }

    private fun creds(
        domain: String,
        id: Long? = null,
        lastUpdated: Long? = null,
    ): LoginCredentials {
        return LoginCredentials(id = id, domain = domain, username = "a", password = "b", lastUpdatedMillis = lastUpdated)
    }
}
