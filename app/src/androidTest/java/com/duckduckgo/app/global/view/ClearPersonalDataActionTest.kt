/*
 * Copyright (c) 2018 DuckDuckGo
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

import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.WebDataManager
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import org.junit.Before
import org.junit.Test

class ClearPersonalDataActionTest {

    private lateinit var testee: ClearPersonalDataAction

    private val mockDataManager: WebDataManager = mock()
    private val mockClearingUnsentForgetAllPixelStore: UnsentForgetAllPixelStore = mock()

    @Before
    fun setup() {
        testee = ClearPersonalDataAction(
            InstrumentationRegistry.getInstrumentation().targetContext,
            mockDataManager,
            mockClearingUnsentForgetAllPixelStore
        )
    }

    @UiThreadTest
    @Test
    fun whenClearCalledThenPixelCountIncremented() {
        testee.clear()
        verify(mockClearingUnsentForgetAllPixelStore).incrementCount()
    }

    @UiThreadTest
    @Test
    fun whenClearCalledThenDataManagerClearsSessions() {
        testee.clear()
        verify(mockDataManager).clearWebViewSessions()
    }

    @UiThreadTest
    @Test
    fun whenClearCalledThenDataManagerClearsData() {
        testee.clear()
        verify(mockDataManager).clearData(any(), any(), any())
    }

    @UiThreadTest
    @Test
    fun whenClearCalledThenDataManagerClearsCookies() {
        testee.clear()
        verify(mockDataManager).clearExternalCookies(any(), any())
    }
}