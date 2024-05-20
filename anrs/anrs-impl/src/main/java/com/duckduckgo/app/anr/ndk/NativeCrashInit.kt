/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.anr.ndk

import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.browser.customtabs.CustomTabDetector
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.lifecycle.VpnProcessLifecycleObserver
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.library.loader.LibraryLoader
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = VpnProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class NativeCrashInit @Inject constructor(
    context: Context,
    @IsMainProcess private val isMainProcess: Boolean,
    private val customTabDetector: CustomTabDetector,
    private val appBuildConfig: AppBuildConfig,
    private val nativeCrashFeature: NativeCrashFeature,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
) : MainProcessLifecycleObserver, VpnProcessLifecycleObserver {

    private val isCustomTab: Boolean by lazy { customTabDetector.isCustomTab() }
    private val processName: String by lazy { if (isMainProcess) "main" else "vpn" }

    init {
        try {
            LibraryLoader.loadLibrary(context, "crash-ndk")
        } catch (ignored: Throwable) {
            logcat(ERROR) { "ndk-crash: Error loading crash-ndk lib: ${ignored.asLog()}" }
        }
    }

    private external fun jni_register_sighandler(logLevel: Int, appVersion: String, processName: String, isCustomTab: Boolean)

    override fun onCreate(owner: LifecycleOwner) {
        if (isMainProcess) {
            coroutineScope.launch {
                jniRegisterNativeSignalHandler()
            }
        } else {
            logcat(ERROR) { "ndk-crash: onCreate wrongly called in a secondary process" }
        }
    }

    override fun onVpnProcessCreated() {
        if (!isMainProcess) {
            coroutineScope.launch {
                jniRegisterNativeSignalHandler()
            }
        } else {
            logcat(ERROR) { "ndk-crash: onCreate wrongly called in the main process" }
        }
    }

    private suspend fun jniRegisterNativeSignalHandler() = withContext(dispatcherProvider.io()) {
        runCatching {
            if (isMainProcess && !nativeCrashFeature.nativeCrashHandling().isEnabled()) return@withContext
            if (!isMainProcess && !nativeCrashFeature.nativeCrashHandlingSecondaryProcess().isEnabled()) return@withContext

            val logLevel = if (appBuildConfig.isDebug || appBuildConfig.isInternalBuild()) {
                Log.VERBOSE
            } else {
                Log.ASSERT
            }
            jni_register_sighandler(logLevel, appBuildConfig.versionName, processName, isCustomTab)
        }.onFailure {
            logcat(ERROR) { "ndk-crash: Error calling jni_register_sighandler: ${it.asLog()}" }
        }
    }
}
