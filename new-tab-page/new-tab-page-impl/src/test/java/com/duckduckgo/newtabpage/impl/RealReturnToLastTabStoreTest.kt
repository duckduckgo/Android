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

package com.duckduckgo.newtabpage.impl

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealReturnToLastTabStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val testee = RealReturnToLastTabStore(context)

    @After
    fun after() {
        context.preferencesDataStoreFile("return_to_last_tab_store").delete()
    }

    @Test
    fun emptyStoreDefaultsToEnabledThenPersistsUpdates() = runTest {
        // Empty store: existing users keep seeing the hatch.
        assertTrue(testee.isEnabled.first())

        testee.setEnabled(false)
        assertFalse(testee.isEnabled.first())

        testee.setEnabled(true)
        assertTrue(testee.isEnabled.first())
    }
}
