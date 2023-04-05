/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.navigation.impl

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.getActivityParams
import java.lang.IllegalArgumentException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class GlobalActivityStarterImplTest {

    private lateinit var globalActivityStarter: GlobalActivityStarter
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        globalActivityStarter = GlobalActivityStarterImpl(setOf(TestActivityMapper()))
    }

    @Test
    fun whenStartIntentNotFoundActivityThenReturnNull() {
        assertNull(globalActivityStarter.startIntent(context, NotFoundParams))
    }

    @Test
    fun whenStartIntentNotFoundActivityThenReturnIntent() {
        val intent = globalActivityStarter.startIntent(context, TestParams("test"))

        assertNotNull(intent)
        assertEquals("test", intent?.getActivityParams(TestParams::class.java)?.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun whenStartNotFoundActivityThenThrow() {
        globalActivityStarter.start(context, NotFoundParams)
    }

    @Test
    fun whenStartNotFoundActivityThenSucceeds() {
        val context: Context = mock()
        globalActivityStarter.start(context, TestParams("test"))

        verify(context).startActivity(any(), anyOrNull())
    }
}

private class TestActivity : AppCompatActivity()
private data class TestParams(val value: String) : GlobalActivityStarter.ActivityParams
private object NotFoundParams : GlobalActivityStarter.ActivityParams
private class TestActivityMapper : GlobalActivityStarter.ParamToActivityMapper {
    override fun map(activityParams: GlobalActivityStarter.ActivityParams): Class<out AppCompatActivity>? {
        return if (activityParams is TestParams) {
            TestActivity::class.java
        } else {
            null
        }
    }
}
