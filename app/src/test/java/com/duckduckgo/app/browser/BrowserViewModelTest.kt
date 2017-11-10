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

package com.duckduckgo.app.browser

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import mock
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class BrowserViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    val observer: Observer<String> = mock()

    @Mock
    val urlConverter: QueryUrlConverter = mock()

    val testee = BrowserViewModel(urlConverter)

    @Test
    fun whenEmptyInputQueryThenNoQueryMadeAvailableToActivity() {
        testee.query.observeForever(observer)
        testee.onQueryEntered("")
        verify(observer, never()).onChanged(ArgumentMatchers.anyString())
    }

    @Test
    fun whenBlankInputQueryThenNoQueryMadeAvailableToActivity() {
        testee.query.observeForever(observer)
        testee.onQueryEntered("     ")
        verify(observer, never()).onChanged(ArgumentMatchers.anyString())
    }

    @Test
    fun whenNonEmptyInputThenQueryMadeAvailableToActivity() {
        Mockito.doReturn(validFullUri()).`when`(urlConverter).convertInputToUri(anyString())

        testee.query.observeForever(observer)
        testee.onQueryEntered("foo")
        verify(observer).onChanged(ArgumentMatchers.anyString())
    }

    private fun validFullUri(): String = "https://duckduckgo.com"
}
