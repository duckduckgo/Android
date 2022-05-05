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

package com.duckduckgo.voice.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RealVoiceSearchRepositoryTest {

    private lateinit var testee: RealVoiceSearchRepository
    private lateinit var dataSource: FakeVoiceSearchDataStore

    @Before
    fun setUp() {
        dataSource = FakeVoiceSearchDataStore()
        testee = RealVoiceSearchRepository(dataSource)
    }

    @Test
    fun whenRationalDialogIsAcceptedThenGetHasAcceptedRationaleDialogShouldBeTrue() {
        assertFalse(testee.getHasAcceptedRationaleDialog())

        testee.acceptRationaleDialog()

        assertTrue(testee.getHasAcceptedRationaleDialog())
    }

    @Test
    fun whenPermissionDeclinedForeverThenGetHasPermissionDeclinedForeverShouldBeTrue() {
        assertFalse(testee.getHasPermissionDeclinedForever())

        testee.declinePermissionForever()

        assertTrue(testee.getHasPermissionDeclinedForever())
    }

    @Test
    fun whenAvailabilityIsLoggedThengetHasLoggedAvailabilityShouldBeTrue() {
        assertFalse(testee.getHasLoggedAvailability())

        testee.saveLoggedAvailability()

        assertTrue(testee.getHasLoggedAvailability())
    }
}

class FakeVoiceSearchDataStore : VoiceSearchDataStore {
    override var permissionDeclinedForever: Boolean = false
    override var userAcceptedRationaleDialog: Boolean = false
    override var availabilityLogged: Boolean = false
}
