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

package com.duckduckgo.duckchat.impl.ui.filechooser.capture.launcher

import android.net.Uri
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.launcher.UploadFromExternalMediaAppLauncher.MediaCaptureResult
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.permission.ExternalMediaSystemPermissionsHelper
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.postprocess.MediaCaptureDelayedDeleter
import com.duckduckgo.duckchat.impl.ui.filechooser.capture.postprocess.MediaCaptureImageMover
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PermissionAwareExternalMediaAppLauncherTest {

    private val mainExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "launcher-test-main") }
    private val ioExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "launcher-test-io") }
    private val dispatchers = object : DispatcherProvider {
        override fun io(): CoroutineDispatcher = ioExecutor.asCoroutineDispatcher()
        override fun main(): CoroutineDispatcher = mainExecutor.asCoroutineDispatcher()
        override fun computation(): CoroutineDispatcher = ioExecutor.asCoroutineDispatcher()
        override fun unconfined(): CoroutineDispatcher = ioExecutor.asCoroutineDispatcher()
    }

    private var moverThread: String? = null
    private val imageMover = object : MediaCaptureImageMover {
        override suspend fun moveInternal(contentUri: Uri): File? {
            moverThread = Thread.currentThread().name
            return File("captured.jpg")
        }
    }

    private lateinit var activityResultCallback: ActivityResultCallback<Uri?>
    private val caller: ActivityResultCaller = mock {
        on { registerForActivityResult(any<ActivityResultContract<String?, Uri?>>(), any()) } doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            activityResultCallback = invocation.arguments[1] as ActivityResultCallback<Uri?>
            mock<ActivityResultLauncher<String?>>()
        }
    }

    private val testee = PermissionAwareExternalMediaAppLauncher(
        permissionHelper = mock<ExternalMediaSystemPermissionsHelper>(),
        imageMover = imageMover,
        delayedDeleter = mock<MediaCaptureDelayedDeleter>(),
        appCoroutineScope = CoroutineScope(SupervisorJob()),
        dispatchers = dispatchers,
    )

    @After
    fun tearDown() {
        mainExecutor.shutdownNow()
        ioExecutor.shutdownNow()
    }

    @Test
    fun whenMediaCapturedThenFileIsMovedOnIoAndResultDeliveredOnMain() {
        val delivered = CountDownLatch(1)
        var result: MediaCaptureResult? = null
        var resultThread: String? = null
        testee.registerForResult(caller) {
            result = it
            resultThread = Thread.currentThread().name
            delivered.countDown()
        }

        activityResultCallback.onActivityResult(mock<Uri>())

        assertTrue(delivered.await(5, TimeUnit.SECONDS))
        assertTrue(result is MediaCaptureResult.MediaCaptured)
        assertTrue(moverThread.orEmpty().contains("launcher-test-io"))
        assertTrue(resultThread.orEmpty().contains("launcher-test-main"))
    }
}
