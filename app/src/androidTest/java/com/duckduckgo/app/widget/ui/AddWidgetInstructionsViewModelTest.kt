/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.widget.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.widget.ui.AddWidgetInstructionsViewModel.Command
import com.duckduckgo.app.widget.ui.AddWidgetInstructionsViewModel.Command.Close
import com.duckduckgo.app.widget.ui.AddWidgetInstructionsViewModel.Command.ShowHome
import com.nhaarman.mockitokotlin2.lastValue
import com.nhaarman.mockitokotlin2.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class AddWidgetInstructionsViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val schedulers = InstantSchedulersRule()

    private lateinit var testee: AddWidgetInstructionsViewModel

    @Mock
    private lateinit var mockCommandObserver: Observer<Command>

    @Captor
    private lateinit var commandCaptor: ArgumentCaptor<Command>

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)
        testee = AddWidgetInstructionsViewModel()
        testee.command.observeForever(mockCommandObserver)
    }

    @After
    fun after() {
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenShowHomePressedThenShowHomeCommandSent() {
        testee.onShowHomePressed()
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(ShowHome, commandCaptor.lastValue)
    }

    @Test
    fun whenClosePressedThenCloseCommandSent() {
        testee.onClosePressed()
        verify(mockCommandObserver).onChanged(commandCaptor.capture())
        assertEquals(Close, commandCaptor.lastValue)
    }
}
