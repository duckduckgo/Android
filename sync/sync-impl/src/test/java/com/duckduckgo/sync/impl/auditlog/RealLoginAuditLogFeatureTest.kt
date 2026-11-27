/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.auditlog

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.impl.SyncFeature
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RealLoginAuditLogFeatureTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)

    private val testee = RealLoginAuditLogFeature(syncFeature, coroutineTestRule.testDispatcherProvider)

    @Test
    fun whenFlagsNotSetThenBothDisabledByDefault() = runTest {
        assertFalse(testee.isAuditLogEnabled())
        assertFalse(testee.isNotificationEnabled())
    }

    @Test
    fun whenBothFlagsDisabledThenBothDisabled() = runTest {
        setFlags(auditLog = false, notification = false)

        assertFalse(testee.isAuditLogEnabled())
        assertFalse(testee.isNotificationEnabled())
    }

    @Test
    fun whenOnlyAuditLogEnabledThenAuditLogEnabledAndNotificationDisabled() = runTest {
        setFlags(auditLog = true, notification = false)

        assertTrue(testee.isAuditLogEnabled())
        assertFalse(testee.isNotificationEnabled())
    }

    @Test
    fun whenOnlyNotificationEnabledThenBothDisabled() = runTest {
        setFlags(auditLog = false, notification = true)

        assertFalse(testee.isAuditLogEnabled())
        assertFalse(testee.isNotificationEnabled())
    }

    @Test
    fun whenBothFlagsEnabledThenBothEnabled() = runTest {
        setFlags(auditLog = true, notification = true)

        assertTrue(testee.isAuditLogEnabled())
        assertTrue(testee.isNotificationEnabled())
    }

    private fun setFlags(auditLog: Boolean, notification: Boolean) {
        syncFeature.loginAuditLog().setRawStoredState(State(enable = auditLog))
        syncFeature.loginNotification().setRawStoredState(State(enable = notification))
    }
}
