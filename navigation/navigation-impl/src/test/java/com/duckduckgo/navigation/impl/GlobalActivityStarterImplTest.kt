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
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.navigation.api.GlobalActivityStarter.DeeplinkActivityParams
import com.duckduckgo.navigation.api.getActivityParams
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class GlobalActivityStarterImplTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val globalActivityStarter = GlobalActivityStarterImpl(setOf(TestActivityWithParamsMapper(), TestActivityNoParamsMapper()))

    @Test
    fun whenStartIntentNotFoundActivityThenReturnNull() {
        assertNull(globalActivityStarter.startIntent(context, NotFoundParams))
    }

    @Test
    fun whenStartIntentNotFoundDeeplinkActivityThenReturnNull() {
        assertNull(globalActivityStarter.startIntent(context, notFoundDeeplinkParams))
    }

    @Test
    fun whenStartIntentWithParamsFindsActivityThenReturnIntent() {
        val intent = globalActivityStarter.startIntent(context, TestParams("test"))

        assertNotNull(intent)
        assertEquals("test", intent?.getActivityParams(TestParams::class.java)?.value)
    }

    @Test
    fun whenStartIntentWithDeeplinkNoParamsFindsActivityThenReturnIntent() {
        val intent = globalActivityStarter.startIntent(context, DeeplinkActivityParams("screenTest"))

        assertNotNull(intent)
        assertNotNull(intent?.getActivityParams(TestNoParams::class.java))
    }

    @Test
    fun whenStartIntentWithDeeplinkParamsFindsActivityThenReturnIntent() {
        val intent = globalActivityStarter.startIntent(context, DeeplinkActivityParams("screenTest", jsonArguments = "{\"value\": \"test\"}"))

        assertNotNull(intent)
        assertEquals("test", intent?.getActivityParams(TestParams::class.java)?.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun whenStartNotFoundActivityThenThrow() {
        globalActivityStarter.start(context, NotFoundParams)
    }

    @Test(expected = IllegalArgumentException::class)
    fun whenStartNotFoundDeeplinkActivityThenThrow() {
        globalActivityStarter.start(context, notFoundDeeplinkParams)
    }

    @Test
    fun whenStartWithParamsFindsActivityThenSucceeds() {
        val context: Context = mock()
        globalActivityStarter.start(context, TestParams("test"))

        verify(context).startActivity(any(), anyOrNull())
    }

    @Test
    fun whenStartWithDeeplinkParamsArgumentsFindsActivityThenSucceeds() {
        val context: Context = mock()
        globalActivityStarter.start(context, DeeplinkActivityParams("screenTest", jsonArguments = "{\"value\": \"test\"}"))

        verify(context).startActivity(any(), anyOrNull())
    }

    @Test
    fun whenStartWithDeeplinkNoParamsArgumentsFindsActivityThenSucceeds() {
        val context: Context = mock()
        globalActivityStarter.start(context, DeeplinkActivityParams("screenTest"))

        verify(context).startActivity(any(), anyOrNull())
    }

    // Fix 2: FLAG_ACTIVITY_NEW_TASK

    @Test
    fun whenStartIntentWithNonActivityContextThenFlagNewTaskAdded() {
        val intent = globalActivityStarter.startIntent(context, TestParams("test"))
        assertNotNull(intent)
        assertNotEquals(0, intent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun whenStartIntentWithActivityContextThenFlagNewTaskNotAdded() {
        val activityContext: Context = mock<AppCompatActivity>()
        val intent = globalActivityStarter.startIntent(activityContext, TestParams("test"))
        assertNotNull(intent)
        assertEquals(0, intent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun whenStartIntentDeeplinkWithNonActivityContextThenFlagNewTaskAdded() {
        val intent = globalActivityStarter.startIntent(context, DeeplinkActivityParams("screenTest"))
        assertNotNull(intent)
        assertNotEquals(0, intent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun whenStartIntentDeeplinkWithActivityContextThenFlagNewTaskNotAdded() {
        val activityContext: Context = mock<AppCompatActivity>()
        val intent = globalActivityStarter.startIntent(activityContext, DeeplinkActivityParams("screenTest"))
        assertNotNull(intent)
        assertEquals(0, intent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // Fix 3: ActivityResultLauncher overloads

    @Test
    fun whenStartWithLauncherParamsFindsActivityThenLauncherIsCalledWithIntent() {
        val launcher: ActivityResultLauncher<Intent> = mock()
        globalActivityStarter.startForResult(context, TestParams("test"), launcher)
        verify(launcher).launch(any())
    }

    @Test
    fun whenStartForResultWithNonActivityContextThenIntentDoesNotContainNewTaskFlag() {
        val launcher: ActivityResultLauncher<Intent> = mock()
        globalActivityStarter.startForResult(context, TestParams("test"), launcher)

        val intentCaptor = argumentCaptor<Intent>()
        verify(launcher).launch(intentCaptor.capture())
        assertEquals(0, intentCaptor.firstValue.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test(expected = IllegalArgumentException::class)
    fun whenStartWithLauncherParamsNotFoundThenThrows() {
        val launcher: ActivityResultLauncher<Intent> = mock()
        globalActivityStarter.startForResult(context, NotFoundParams, launcher)
    }

    @Test
    fun whenStartWithLauncherDeeplinkFindsActivityThenLauncherIsCalledWithIntent() {
        val launcher: ActivityResultLauncher<Intent> = mock()
        globalActivityStarter.startForResult(context, DeeplinkActivityParams("screenTest"), launcher)
        verify(launcher).launch(any())
    }

    @Test
    fun whenStartForResultWithDeeplinkAndNonActivityContextThenIntentDoesNotContainNewTaskFlag() {
        val launcher: ActivityResultLauncher<Intent> = mock()
        globalActivityStarter.startForResult(context, DeeplinkActivityParams("screenTest"), launcher)

        val intentCaptor = argumentCaptor<Intent>()
        verify(launcher).launch(intentCaptor.capture())
        assertEquals(0, intentCaptor.firstValue.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test(expected = IllegalArgumentException::class)
    fun whenStartWithLauncherDeeplinkNotFoundThenThrows() {
        val launcher: ActivityResultLauncher<Intent> = mock()
        globalActivityStarter.startForResult(context, notFoundDeeplinkParams, launcher)
    }

    // Fix 4: duplicate mapper warning — behavior is that first match wins, no exception

    @Test
    fun whenMultipleMappersMatchSameParamsThenFirstMatchIsUsedWithoutException() {
        val starterWithDuplicates = GlobalActivityStarterImpl(setOf(TestActivityWithParamsMapper(), DuplicateTestParamsMapper()))
        val intent = starterWithDuplicates.startIntent(context, TestParams("test"))
        assertNotNull(intent)
    }
}

private class TestActivity : AppCompatActivity()
private data class TestParams(val value: String) : ActivityParams
private object NotFoundParams : ActivityParams

private val notFoundDeeplinkParams = DeeplinkActivityParams("notFoundScreen")
private object TestNoParams : ActivityParams

private class TestActivityNoParamsMapper : GlobalActivityStarter.ParamToActivityMapper {
    override fun map(activityParams: ActivityParams): Class<out AppCompatActivity>? {
        return if (activityParams is TestNoParams) {
            TestActivity::class.java
        } else {
            null
        }
    }

    override fun map(deeplinkActivityParams: DeeplinkActivityParams): ActivityParams? {
        val screenName = deeplinkActivityParams.screenName.takeUnless { it.isEmpty() } ?: return null
        return if (screenName == "screenTest") {
            TestNoParams
        } else {
            null
        }
    }
}

private class DuplicateTestParamsMapper : GlobalActivityStarter.ParamToActivityMapper {
    override fun map(activityParams: ActivityParams): Class<out AppCompatActivity>? =
        if (activityParams is TestParams) TestActivity::class.java else null

    override fun map(deeplinkActivityParams: DeeplinkActivityParams): ActivityParams? = null
}

private class TestActivityWithParamsMapper : GlobalActivityStarter.ParamToActivityMapper {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    override fun map(activityParams: ActivityParams): Class<out AppCompatActivity>? {
        return if (activityParams is TestParams) {
            TestActivity::class.java
        } else {
            null
        }
    }

    override fun map(deeplinkActivityParams: DeeplinkActivityParams): ActivityParams? {
        val screenName = deeplinkActivityParams.screenName.takeUnless { it.isEmpty() } ?: return null
        return if (screenName == "screenTest") {
            if (deeplinkActivityParams.jsonArguments.isEmpty()) {
                val instance = tryCreateObjectInstance(TestParams::class.java)
                if (instance != null) {
                    return instance
                }
            }
            tryCreateActivityParams(TestParams::class.java, deeplinkActivityParams.jsonArguments)
        } else {
            null
        }
    }

    private fun tryCreateObjectInstance(clazz: Class<out ActivityParams>): ActivityParams? {
        return kotlin.runCatching {
            Types.getRawType(clazz).kotlin.objectInstance as ActivityParams
        }.getOrNull()
    }

    private fun tryCreateActivityParams(
        clazz: Class<out ActivityParams>,
        jsonArguments: String,
    ): ActivityParams? {
        return kotlin.runCatching {
            moshi.adapter(clazz).fromJson(jsonArguments)
        }.getOrNull()
    }
}
