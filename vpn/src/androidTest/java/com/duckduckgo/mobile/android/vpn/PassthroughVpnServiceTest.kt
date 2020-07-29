/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PassthroughVpnServiceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val serviceRule = ServiceTestRule()

    @After
    fun tearDown() {
        context.startService(stopIntent())
    }

    @Test
    fun whenCreatedDoesNotAutomaticallyRun() {
        assertFalse(PassthroughVpnService.running)
    }

    @Test
    fun whenRunIntentSentThenServiceRunning() {
        serviceRule.startService(startIntent())
        assertTrue(PassthroughVpnService.running)
    }

    private fun startIntent(): Intent {
        return PassthroughVpnService.startIntent(context)
    }

    private fun stopIntent(): Intent {
        return PassthroughVpnService.stopIntent(context)
    }
}