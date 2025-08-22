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

package com.duckduckgo.pir.impl.dashboard.state

import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.pir.impl.models.Address
import com.duckduckgo.pir.impl.models.ProfileQuery
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(ActivityScope::class)
class PirWebOnboardingStateHolder @Inject constructor() {

    private val names: MutableList<Name> = mutableListOf()
    private val addresses: MutableList<Address> = mutableListOf()
    private var birthYear: Int = 0

    val isProfileComplete: Boolean
        get() = names.isNotEmpty() && addresses.isNotEmpty() && birthYear > 0

    fun addAddress(
        city: String,
        state: String,
    ): Boolean {
        if (addresses.any { it.city == city && it.state == state }) {
            return false
        }
        addresses.add(Address(city, state))
        return true
    }

    fun addName(
        firstName: String,
        middleName: String? = null,
        lastName: String,
    ): Boolean {
        if (names.any { it.firstName == firstName && it.middleName == middleName && it.lastName == lastName }) {
            return false
        }
        names.add(
            Name(
                firstName = firstName,
                lastName = lastName,
                middleName = middleName,
            ),
        )
        return true
    }

    fun setBirthYear(year: Int): Boolean {
        birthYear = year
        return true
    }

    fun setNameAtIndex(
        index: Int,
        firstName: String,
        middleName: String? = null,
        lastName: String,
    ): Boolean {
        if (index !in names.indices) {
            return false
        }

        // duplicates not allowed
        if (names.any { it.firstName == firstName && it.middleName == middleName && it.lastName == lastName }) {
            return false
        }

        names[index] = Name(
            firstName = firstName,
            lastName = lastName,
            middleName = middleName,
        )
        return true
    }

    fun setAddressAtIndex(
        index: Int,
        city: String,
        state: String,
    ): Boolean {
        if (index !in addresses.indices) {
            return false
        }

        // duplicates not allowed
        if (addresses.any { it.city == city && it.state == state }) {
            return false
        }

        addresses[index] = Address(city, state)
        return true
    }

    fun removeAddressAtIndex(index: Int): Boolean {
        if (index in addresses.indices) {
            addresses.removeAt(index)
            return true
        }
        return false
    }

    fun removeNameAtIndex(index: Int): Boolean {
        if (index in names.indices) {
            names.removeAt(index)
            return true
        }
        return false
    }

    fun toProfileQueries(currentYear: Int): List<ProfileQuery> {
        val profiles = mutableListOf<ProfileQuery>()
        names.forEach { name ->
            addresses.forEach { address ->
                profiles.add(
                    ProfileQuery(
                        id = 0, // ID is auto-generated in the database
                        firstName = name.firstName,
                        lastName = name.lastName,
                        middleName = name.middleName,
                        city = address.city,
                        state = address.state,
                        addresses = listOf(address),
                        birthYear = birthYear,
                        fullName = name.middleName?.let { middleName ->
                            "${name.firstName} $middleName ${name.lastName}"
                        } ?: "${name.firstName} ${name.lastName}",
                        age = currentYear - birthYear,
                        deprecated = false,
                    ),
                )
            }
        }
        return profiles
    }

    fun clear() {
        names.clear()
        addresses.clear()
        birthYear = 0
    }

    private data class Name(
        val firstName: String,
        val lastName: String,
        val middleName: String? = null,
    )
}
