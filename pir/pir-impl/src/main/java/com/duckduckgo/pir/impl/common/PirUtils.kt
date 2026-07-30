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
import com.duckduckgo.pir.impl.scripts.models.AddressCityStateParams
import com.duckduckgo.pir.impl.scripts.models.ExtractedProfileParams

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
        addresses =
        this.addresses
            .map {
                AddressCityStateParams(
                    city = it.city,
                    state = it.state,
                    fullAddress = it.fullAddress,
                    extras = it.extras.ifEmpty { null },
                )
            }.ifEmpty { null },
        phoneNumbers = this.phoneNumbers.ifEmpty { null },
        relatives = this.relatives.ifEmpty { null },
        alternativeNames = this.alternativeNames.ifEmpty { null },
        identifier = this.identifier.ifEmpty { null },
        extras = this.extras.ifEmpty { null },
    )
}

internal fun ExtractedProfile.hasMatchingProfileOnParent(extractedProfiles: List<ExtractedProfile>): Boolean {
    return extractedProfiles.any {
        it.brokerName == this.brokerName && this.matches(it)
    }
}

internal fun ExtractedProfile.matches(extractedProfile: ExtractedProfile): Boolean {
    return this.name == extractedProfile.name && this.age == extractedProfile.age &&
        this.alternativeNames.isASubSetOrSuperSetOf(extractedProfile.alternativeNames) &&
        this.relatives.isASubSetOrSuperSetOf(extractedProfile.relatives) &&
        // extras are an open bag driven by broker config; including them would break mirror-site dedup whenever a new field ships
        this.addresses.withoutExtras().isASubSetOrSuperSetOf(extractedProfile.addresses.withoutExtras())
}

private fun List<AddressCityState>.withoutExtras(): List<AddressCityState> = map { it.copy(extras = emptyMap()) }

private fun <T> List<T>.isASubSetOrSuperSetOf(other: List<T>): Boolean {
    return this.containsAll(other) || other.containsAll(this)
}
