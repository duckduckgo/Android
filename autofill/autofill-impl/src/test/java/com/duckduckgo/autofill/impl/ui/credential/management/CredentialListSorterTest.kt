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
import com.duckduckgo.autofill.impl.ui.credential.management.sorting.CredentialListSorterByTitleAndDomain
import com.duckduckgo.autofill.impl.urlmatcher.AutofillDomainNameUrlMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CredentialListSorterTest {

    private val unicodeNormalizer = TestUrlUnicodeNormalizer()
    private val autofillUrlMatcher = AutofillDomainNameUrlMatcher(unicodeNormalizer)

    private val testee = CredentialListSorterByTitleAndDomain(autofillUrlMatcher = autofillUrlMatcher)
    private val list = mutableListOf<LoginCredentials>()

    @Test
    fun whenListIsEmptyThenReturnEmptyList() {
        val result = testee.sort(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun whenComparingTitlesCaseIsIgnored() {
        val sorted = testee.sort(
            list.also {
                it.add(credsWithTitle("C"))
                it.add(credsWithTitle("b"))
                it.add(credsWithTitle("A"))
            },
        )
        sorted.assertTitleOrder("A", "b", "C")
    }

    @Test
    fun whenComparingDomainsCaseIsIgnored() {
        val sorted = testee.sort(
            list.also {
                it.add(credsWithDomain("C.COM"))
                it.add(credsWithDomain("b.com"))
                it.add(credsWithDomain("A.Com"))
            },
        )
        sorted.assertDomainOrder("A.Com", "b.com", "C.COM")
    }

    @Test
    fun whenComparingSubdomainsCaseIsIgnored() {
        val sorted = testee.sort(
            list.also {
                it.add(credsWithDomain("C.example.COM"))
                it.add(credsWithDomain("b.example.com"))
                it.add(credsWithDomain("A.example.Com"))
            },
        )
        sorted.assertDomainOrder("A.example.Com", "b.example.com", "C.example.COM")
    }

    @Test
    fun whenTitleStartsWithANumberThenSortedBeforeLetters() {
        val sorted = testee.sort(
            list.also {
                it.add(credsWithTitle("1"))
                it.add(credsWithTitle("A"))
                it.add(credsWithTitle("2"))
                it.add(credsWithTitle("B"))
            },
        )
        sorted.assertTitleOrder("1", "2", "A", "B")
    }

    @Test
    fun whenTitleStartsWithACharacterThenSortedBeforeLetters() {
        val sorted = testee.sort(
            list.also {
                it.add(credsWithTitle("1"))
                it.add(credsWithTitle("$"))
                it.add(credsWithTitle("2"))
                it.add(credsWithTitle("A"))
            },
        )
        sorted.assertTitleOrder("$", "1", "2", "A")
    }

    @Test
    fun whenTitleMissingThenSortedBeforeLetters() {
        val sorted = testee.sort(
            list.also {
                it.add(credsWithTitle("A"))
                it.add(credsWithTitle("B"))
                it.add(credsWithTitle("C"))
                it.add(credsWithTitle(null))
            },
        )
        sorted.assertTitleOrder(null, "A", "B", "C")
    }

    @Test
    fun whenTitlesAllMissingThenDomainUsedInstead() {
        val sorted = testee.sort(
            list.also {
                it.add(credsWithDomain("a.com"))
                it.add(credsWithDomain("c.com"))
                it.add(credsWithDomain("b.com"))
            },
        )
        sorted.assertDomainOrder("a.com", "b.com", "c.com")
    }

    @Test
    fun whenTitlesEqualThenDomainUsedAsSecondarySort() {
        val sorted = testee.sort(
            list.also {
                it.add(creds("a.com", "Website"))
                it.add(creds("c.com", "Website"))
                it.add(creds("b.com", "Website"))
            },
        )
        sorted.assertDomainOrder("a.com", "b.com", "c.com")
    }

    @Test
    fun whenTitlesDifferThenDomainSortingNotUsed() {
        val sorted = testee.sort(
            list.also {
                it.add(creds("a.com", "2. Website"))
                it.add(creds("c.com", "1. Website"))
                it.add(creds("b.com", "3. Website"))
            },
        )
        sorted.assertTitleOrder("1. Website", "2. Website", "3. Website")
        sorted.assertDomainOrder("c.com", "a.com", "b.com")
    }

    @Test
    fun whenComparingDomainsThenHttpSchemaIgnored() {
        val sorted = testee.sort(
            list.also {
                it.add(credsWithDomain("http://b.com"))
                it.add(credsWithDomain("a.com"))
            },
        )
        sorted.assertDomainOrder("a.com", "http://b.com")
    }

    @Test
    fun whenComparingDomainsThenHttpsSchemaIgnored() {
        val sorted = testee.sort(
            list.also {
                it.add(credsWithDomain("https://b.com"))
                it.add(credsWithDomain("a.com"))
            },
        )
        sorted.assertDomainOrder("a.com", "https://b.com")
    }

    @Test
    fun whenComparingSubdomainsThenWwwNotTreatedAsSpecialForSorting() {
        val sorted = testee.sort(
            list.also {
                it.add(credsWithDomain(id = 0, domain = "www.example.com"))
                it.add(credsWithDomain(id = 1, domain = "accounts.example.com"))
                it.add(credsWithDomain(id = 2, domain = "z.example.com"))
            },
        )
        sorted.assertIdOrder(1, 0, 2)
    }

    @Test
    fun whenComparingDomainsThenMissingDomainSortedFirst() {
        val sorted = testee.sort(
            list.also {
                it.add(credsWithDomain("http://b.com"))
                it.add(credsWithDomain("c.com"))
                it.add(credsWithDomain(null))
            },
        )
        sorted.assertDomainOrder(null, "http://b.com", "c.com")
    }

    @Test
    fun whenComparingDomainsThenInvalidDomainInitialUsedForSort() {
        val sorted = testee.sort(
            list.also {
                it.add(credsWithDomain("http://b.com"))
                it.add(credsWithDomain("c.com"))
                it.add(credsWithDomain("an invalid domain"))
                it.add(credsWithDomain("invalid domain"))
            },
        )
        sorted.assertDomainOrder("an invalid domain", "http://b.com", "c.com", "invalid domain")
    }

    @Test
    fun whenCombinationOfDomainsAndTitlesThenTitlesTakePreferenceWhenTheyExist() {
        val credentials = listOf(
            creds(domain = "energy.com"),
            creds(domain = "amazon.co.uk", title = "Smile Amazon"),
            creds(domain = "example.com", title = "c"),
            creds(domain = "aaa.com"),
            creds(domain = "bar.com"),
        )

        val sorted = testee.sort(credentials)
        sorted.assertDomainOrder("aaa.com", "bar.com", "example.com", "energy.com", "amazon.co.uk")
        sorted.assertTitleOrder(null, null, "c", null, "Smile Amazon")
    }

    @Test
    fun whenSpecialCharactersInDomainThenSortedAmongLetters() {
        val sorted = testee.sort(
            list.also {
                it.add(credsWithDomain("a.com"))
                it.add(credsWithDomain("b.com"))
                it.add(credsWithDomain("ç.com"))
                it.add(credsWithDomain("c.com"))
            },
        )
        sorted.assertDomainOrder("a.com", "b.com", "c.com", "ç.com")
    }

    @Test
    fun whenSpecialCharactersInTitleThenSortedAmongLetters() {
        val sorted = testee.sort(
            list.also {
                it.add(credsWithTitle("a"))
                it.add(credsWithTitle("c"))
                it.add(credsWithTitle("b"))
                it.add(credsWithTitle("ć"))
            },
        )
        sorted.assertTitleOrder("a", "b", "c", "ć")
    }

    @Test
    fun whenMultipleSimilarCharactersInTitleThenSortingCorrect() {
        val sorted = testee.sort(
            list.also {
                it.add(credsWithTitle("a"))
                it.add(credsWithTitle("CNN"))
                it.add(credsWithTitle("cello"))
                it.add(credsWithTitle("b"))
                it.add(credsWithTitle("ć"))
                it.add(credsWithTitle("d"))
            },
        )
        sorted.assertTitleOrder("a", "b", "ć", "cello", "CNN", "d")
    }

    @Test
    fun whenMultipleSimilarCharactersInDomainThenSortingCorrect() {
        val sorted = testee.sort(
            list.also {
                it.add(credsWithDomain("a"))
                it.add(credsWithDomain("CNN"))
                it.add(credsWithDomain("cello"))
                it.add(credsWithDomain("b"))
                it.add(credsWithDomain("ć"))
                it.add(credsWithDomain("d"))
            },
        )
        sorted.assertDomainOrder("a", "b", "ć", "cello", "CNN", "d")
    }

    @Test
    fun whenComparingSubdomainsWithSitesFromSameDomainWithoutSubdomainThenTopSiteSortedFirst() {
        val sorted = testee.sort(
            list.also {
                it.add(credsWithDomain(id = 0, domain = "www.example.com"))
                it.add(credsWithDomain(id = 1, domain = "accounts.example.com"))
                it.add(credsWithDomain(id = 2, domain = "example.com"))
            },
        )
        sorted.assertIdOrder(2, 1, 0)
    }

    @Test
    fun whenComparingMixtureOfTitlesSitesWithSubdomainsAndSomeWithoutThenCorrectOrder() {
        val sorted = testee.sort(
            list.also {
                it.add(creds(id = 0, domain = "www.exaaaaample.com"))
                it.add(creds(id = 1, domain = "www.example.com"))
                it.add(creds(id = 2, domain = "accounts.example.com"))
                it.add(creds(id = 3, domain = "example.com"))
                it.add(creds(id = 4, title = "Example"))
                it.add(creds(id = 5, title = "www.Example.com"))
            },
        )
        sorted.assertIdOrder(0, 4, 3, 2, 1, 5)
    }

    @Test
    fun whenDomainsStartWithDecomposableLetterThenIsDecomposedInOrdering() {
        unicodeNormalizer.overrides["ç.com"] = "c.com"
        val sorted = testee.sort(
            list.also {
                it.add(creds(id = 0, domain = "a.com"))
                it.add(creds(id = 1, domain = "b.com"))
                it.add(creds(id = 2, domain = "ç.com"))
                it.add(creds(id = 3, domain = "d.com"))
            },
        )
        sorted.assertIdOrder(0, 1, 2, 3)
    }

    private fun List<LoginCredentials>.assertTitleOrder(vararg titles: String?) {
        assertEquals("Wrong number of titles", titles.size, this.size)
        titles.forEachIndexed { index, title ->
            assertEquals(
                "Order is wrong.\n${titles.joinToString()} " +
                    "[Expected]\n${this.joinToString { it.domainTitle.toString() }} " +
                    "[Actual]\nID wrong at position $index",
                title,
                this[index].domainTitle,
            )
        }
    }

    private fun List<LoginCredentials>.assertDomainOrder(vararg domains: String?) {
        assertEquals("Wrong number of domains", domains.size, this.size)
        domains.forEachIndexed { index, domain ->
            assertEquals(
                "Order is wrong.\n${domains.joinToString()} " +
                    "[Expected]\n${this.joinToString { it.domain.toString() }} " +
                    "[Actual]\nID wrong at position $index",
                domain,
                this[index].domain,
            )
        }
    }

    private fun List<LoginCredentials>.assertIdOrder(vararg ids: Long?) {
        assertEquals("Wrong number of IDs", ids.size, this.size)
        ids.forEachIndexed { index, id ->
            assertEquals(
                "Order is wrong.\n${ids.joinToString()} " +
                    "[Expected]\n${this.joinToString { it.id.toString() }} " +
                    "[Actual]\nID wrong at position $index",
                id,
                this[index].id,
            )
        }
    }

    private fun creds(
        domain: String? = null,
        title: String? = null,
        id: Long = 0,
    ): LoginCredentials {
        return LoginCredentials(domainTitle = title, domain = domain, id = id, username = null, password = null)
    }

    private fun credsWithTitle(
        title: String?,
        id: Long = 0,
    ): LoginCredentials {
        return LoginCredentials(domainTitle = title, id = id, domain = null, username = null, password = null)
    }

    private fun credsWithDomain(
        domain: String?,
        id: Long = 0,
    ): LoginCredentials {
        return LoginCredentials(domain = domain, id = id, domainTitle = null, username = null, password = null)
    }
}
