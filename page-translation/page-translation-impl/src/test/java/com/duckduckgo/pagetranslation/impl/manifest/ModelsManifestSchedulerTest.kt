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

package com.duckduckgo.pagetranslation.impl.manifest

import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.pagetranslation.impl.PageTranslationFeature
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@RunWith(AndroidJUnit4::class)
class ModelsManifestSchedulerTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(PageTranslationFeature::class.java, ioDispatcher = coroutineRule.testDispatcher)
    private val workManager: WorkManager = mock()
    private val owner: LifecycleOwner = mock()
    private val scheduler = ModelsManifestScheduler(feature, coroutineRule.testScope, coroutineRule.testDispatcherProvider, workManager)

    @Test
    fun skipEnqueuingWhenTranslationIsOffDespiteParentBeingOn() {
        feature.self().setRawStoredState(State(enable = true))

        scheduler.onCreate(owner)

        verifyNoInteractions(workManager)
    }

    @Test
    fun enqueueOnceWhenTranslationIsOnAtLaunchDespiteLaterChanges() {
        feature.translation().setRawStoredState(State(enable = true))

        scheduler.onCreate(owner)

        feature.translation().setRawStoredState(State(enable = false))
        feature.translation().setRawStoredState(State(enable = true))

        verifyConnectedWorkEnqueuedOnce()
    }

    @Test
    fun enqueueOnceWhenTranslationTurnsOnAfterLaunch() {
        feature.self().setRawStoredState(State(enable = true))

        scheduler.onCreate(owner)

        verifyNoInteractions(workManager)

        feature.translation().setRawStoredState(State(enable = true))

        verifyConnectedWorkEnqueuedOnce()
    }

    private fun verifyConnectedWorkEnqueuedOnce() {
        val request = argumentCaptor<OneTimeWorkRequest>()

        verify(workManager).enqueueUniqueWork(eq(MANIFEST_WORK_NAME), eq(ExistingWorkPolicy.KEEP), request.capture())

        assertEquals(NetworkType.CONNECTED, request.firstValue.workSpec.constraints.requiredNetworkType)
        assertEquals(ModelsManifestWorker::class.java.name, request.firstValue.workSpec.workerClassName)
    }

    private companion object {
        const val MANIFEST_WORK_NAME = "pageTranslationModelsManifest"
    }
}
