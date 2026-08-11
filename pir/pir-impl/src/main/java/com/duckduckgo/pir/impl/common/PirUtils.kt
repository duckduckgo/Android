/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.common

import com.duckduckgo.pir.impl.models.AddressCityState
import com.duckduckgo.pir.impl.models.ExtractedProfile
import com.duckduckgo.pir.impl.scripts.models.ExtractedProfileParams
import com.duckduckgo.pir.impl.scripts.models.ExtractedProfileParams.AddressParams

internal fun <T> List<T>.splitIntoParts(parts: Int): List<List<T>> {
    if (this.isEmpty()) {
        return emptyList()
    }

    val partSize = this.size / parts
    val remainder = this.size % parts

    val result = mutableListOf<List<T>>()
    var startIndex = 0

    for (i in 0 until parts) {
        val currentPartSize = partSize + if (i < remainder) 1 else 0
        val endIndex = startIndex + currentPartSize

        result.add(this.subList(startIndex, endIndex))
        startIndex = endIndex
    }

    return result
}

internal fun ExtractedProfile.toParams(fullName: String): ExtractedProfileParams {
    return ExtractedProfileParams(
        name = this.name.ifEmpty { null },
        profileUrl = this.profileUrl.ifEmpty { null },
        fullName = fullName.ifEmpty { null },
        email = this.email.ifEmpty { null },
        age = this.age.ifEmpty { null },
        addresses = this.addresses.map {
            AddressParams(
                city = it.city,
                state = it.state,
                extras = it.extras,
            )
        },
        phoneNumbers = this.phoneNumbers,
        relatives = this.relatives,
        identifier = this.identifier.ifEmpty { null },
        extras = this.extras,
    )
}

/**
 * Two addresses describe the same place when their city and state match. Everything else on an
 * address is detail scraped alongside it. Mirrors `sameCityState` in C-S-S.
 */
internal fun AddressCityState.sameCityState(other: AddressCityState): Boolean =
    this.city == other.city && this.state == other.state

/**
 * Returns [scraped] with the attributes we own locally carried over from the stored record.
 *
 * Extras are merged rather than replaced: a key absent from the new scrape keeps the value we
 * already hold, so a broker changing its page layout doesn't strip fields that a pending opt-out
 * still needs. Address extras are merged onto the address with the same city and state.
 */
internal fun ExtractedProfile.refreshedWith(scraped: ExtractedProfile): ExtractedProfile {
    val storedAddresses = this.addresses
    return scraped.copy(
        dbId = this.dbId,
        dateAddedInMillis = this.dateAddedInMillis,
        deprecated = this.deprecated,
        extras = this.extras + scraped.extras,
        addresses = scraped.addresses.map { address ->
            val stored = storedAddresses.firstOrNull { it.sameCityState(address) }
            address.copy(extras = stored?.extras.orEmpty() + address.extras)
        },
    )
}

/**
 * Extras are excluded wherever we compare profiles for identity: a broker config that starts
 * scraping a new field must not make a record we already hold look like a different one.
 */
internal fun List<AddressCityState>.withoutExtras(): List<AddressCityState> = this.map { it.copy(extras = emptyMap()) }

internal data class ExtractedProfileKey(
    val profileQueryId: Long,
    val brokerName: String,
    val name: String,
    val profileUrl: String,
    val identifier: String,
)

internal fun ExtractedProfile.toKey(): ExtractedProfileKey =
    ExtractedProfileKey(
        profileQueryId = this.profileQueryId,
        brokerName = this.brokerName,
        name = this.name,
        profileUrl = this.profileUrl,
        identifier = this.identifier,
    )

internal fun ExtractedProfile.hasMatchingProfileOnParent(extractedProfiles: List<ExtractedProfile>): Boolean {
    return extractedProfiles.any {
        it.brokerName == this.brokerName && this.matches(it)
    }
}

internal fun ExtractedProfile.matches(extractedProfile: ExtractedProfile): Boolean {
    return this.name == extractedProfile.name && this.age == extractedProfile.age &&
            this.alternativeNames.isASubSetOrSuperSetOf(extractedProfile.alternativeNames) &&
            this.relatives.isASubSetOrSuperSetOf(extractedProfile.relatives) &&
            this.addresses.withoutExtras().isASubSetOrSuperSetOf(extractedProfile.addresses.withoutExtras())
}

private fun <T> List<T>.isASubSetOrSuperSetOf(other: List<T>): Boolean {
    return this.containsAll(other) || other.containsAll(this)
}
