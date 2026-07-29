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

package com.duckduckgo.app.global.view

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.dataclearing.api.fire.FireDialogProvider.FireDialogOrigin
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FireDialogProviderImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: FireDialogProviderImpl

    @Before
    fun setup() {
        testee = FireDialogProviderImpl()
    }

    @Test
    fun `createFireDialog always returns SingleTabFireDialog`() = runTest {
        val dialog = testee.createFireDialog(FireDialogOrigin.Browser)

        assertTrue(dialog is SingleTabFireDialog)
    }

    @Test
    fun `createFireDialog from settings returns SingleTabFireDialog`() = runTest {
        val dialog = testee.createFireDialog(FireDialogOrigin.Settings)

        assertTrue(dialog is SingleTabFireDialog)
    }
}
