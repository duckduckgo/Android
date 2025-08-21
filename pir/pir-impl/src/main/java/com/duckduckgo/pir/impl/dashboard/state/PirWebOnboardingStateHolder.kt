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
import com.duckduckgo.pir.impl.store.db.Address
import com.duckduckgo.pir.impl.store.db.UserName
import com.duckduckgo.pir.impl.store.db.UserProfile
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(ActivityScope::class)
class PirWebOnboardingStateHolder @Inject constructor() {

    private val names: MutableList<UserName> = mutableListOf()
    private val addresses: MutableList<Address> = mutableListOf()
    private var birthYear: Int? = null

    val isProfileComplete: Boolean
        get() = names.isNotEmpty() && addresses.isNotEmpty() && (birthYear ?: 0) > 0

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
            UserName(
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

        names[index] = UserName(
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

    fun toUserProfiles(): List<UserProfile> {
        val profiles = mutableListOf<UserProfile>()
        names.forEach { name ->
            addresses.forEach { address ->
                profiles.add(
                    UserProfile(
                        userName = name,
                        birthYear = birthYear ?: 0,
                        addresses = address,
                    ),
                )
            }
        }
        return profiles
    }
}
