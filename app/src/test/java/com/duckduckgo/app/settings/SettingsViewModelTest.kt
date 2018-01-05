/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.settings

import android.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.browser.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var testee: SettingsViewModel

    @Before
    fun before() {
        testee = SettingsViewModel()
    }

    @Test
    fun whenStartNotCalledYetThenViewStateInitialisedDefaultValues() {
        assertNotNull(testee.viewState)

        val value = testee.viewState.value!!
        assertEquals(true, value.loading)
        assertEquals("", value.version)
    }

   @Test
   fun whenStartCalledThenLoadingSetToFalse() {
       testee.start()
       val value = testee.viewState.value!!
       assertEquals(false, value.loading)
   }

    @Test
    fun whenStartCalledThenVersionSetCorrectly() {
        testee.start()
        val value = testee.viewState.value!!
        assertEquals("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", value.version)
    }
}