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

package com.duckduckgo.autofill.impl.ui.credential.management.sorting

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.AutofillDomainFormatter
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import java.text.Collator
import javax.inject.Inject

interface CredentialListSorter {
    fun sort(credentials: List<LoginCredentials>): List<LoginCredentials>
    fun comparator(): Collator
}

@ContributesBinding(FragmentScope::class)
class CredentialListSorterByTitleAndDomain @Inject constructor(
    private val domainFormatter: AutofillDomainFormatter,
) : CredentialListSorter {

    override fun sort(credentials: List<LoginCredentials>): List<LoginCredentials> {
        return credentials.sortedWith(credentialComparator)
    }

    override fun comparator(): Collator {
        return buildCollator()
    }

    private val credentialComparator = object : Comparator<LoginCredentials> {

        /**
         * Compare two credentials by title and then domain.
         */
        override fun compare(o1: LoginCredentials?, o2: LoginCredentials?): Int {
            if (o1 == null && o2 == null) return 0
            if (o1 == null) return -1
            if (o2 == null) return 1

            val collator = buildCollator()
            val title1 = (o1.domainTitle)?.uppercase()
            val title2 = (o2.domainTitle)?.uppercase()
            val domain1 = domainFormatter.extractDomain(o1.domain)
            val domain2 = domainFormatter.extractDomain(o2.domain)

            // if both titles are null, we have to compare domains
            if (title1 == null && title2 == null) return collator.compareFields(domain1, domain2)

            // if first title is null, compare its domain to the second one's title
            if (title1 == null) return collator.compareFields(domain1, title2)

            // if the second title is null, compare its domain to the first one's title
            if (title2 == null) return collator.compareFields(title1, domain2)

            var fieldComparison = collator.compareFields(title1, title2)

            // if titles are exactly equal, we have to compare the two domains
            if (fieldComparison == 0) {
                fieldComparison = collator.compareFields(domain1, domain2)
            }
            return fieldComparison
        }
    }

    private fun Collator.compareFields(field1: String?, field2: String?): Int {
        if (field1 == null && field2 == null) return 0
        if (field1 == null) return -1
        if (field2 == null) return 1
        return getCollationKey(field1).compareTo(getCollationKey(field2))
    }

    fun buildCollator(): Collator {
        val coll: Collator = Collator.getInstance()
        coll.strength = Collator.SECONDARY
        return coll
    }
}
