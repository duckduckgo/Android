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
package com.duckduckgo.pagetranslation.impl.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RealTranslationPreferencesStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file by lazy { File(folder.root, "page_translation.preferences_pb") }
    private val scope = CoroutineScope(Job() + UnconfinedTestDispatcher())

    private fun dataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private val store = RealTranslationPreferencesStore(dataStore(scope))

    @Test
    fun whenNothingWasWrittenThenAskToTranslatePagesIsOn() = runTest {
        assertTrue(store.askToTranslatePages.first())
    }

    @Test
    fun whenAskToTranslatePagesIsTurnedOffThenItIsReadBackAsOff() = runTest {
        store.setAskToTranslatePages(false)

        assertFalse(store.askToTranslatePages.first())
    }

    @Test
    fun whenAskToTranslatePagesIsTurnedBackOnThenItIsReadBackAsOn() = runTest {
        store.setAskToTranslatePages(false)
        store.setAskToTranslatePages(true)

        assertTrue(store.askToTranslatePages.first())
    }

    @Test
    fun whenAskToTranslatePagesIsTurnedOffThenItSurvivesANewDataStoreOnTheSameFile() = runTest {
        store.setAskToTranslatePages(false)
        (scope.coroutineContext[Job] as Job).cancelAndJoin()

        val reopened = RealTranslationPreferencesStore(dataStore(CoroutineScope(Job() + UnconfinedTestDispatcher())))

        assertFalse(reopened.askToTranslatePages.first())
    }
}
