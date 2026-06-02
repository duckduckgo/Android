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

package com.duckduckgo.app.fire

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FireActivityTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun whenFireActivityIntentBuiltThenNoExtraIsANestedIntent() {
        // Android 16 intent-redirection hardening: FireActivity must never receive a nested Parcelable
        // Intent in its extras, otherwise launching it trips UnsafeIntentLaunchViolation. Guard against
        // a regression that re-introduces a forwarded Intent.
        val intent = FireActivity.fireActivityIntent(context, notifyDataCleared = true, deletedTabCount = 3)

        val extras = intent.extras
        assertTrue("Expected primitive extras to be present", extras != null && !extras.isEmpty)
        extras!!.keySet().forEach { key ->
            @Suppress("DEPRECATION")
            assertFalse("Extra '$key' must not be a nested Intent", extras.get(key) is Intent)
        }
    }

    @Test
    fun whenFireActivityIntentBuiltThenTargetsFireActivity() {
        val intent = FireActivity.fireActivityIntent(context, notifyDataCleared = false, deletedTabCount = 0)

        assertTrue(intent.component?.className == FireActivity::class.java.name)
    }
}
