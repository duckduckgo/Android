/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.sync.lib

import android.content.Context
import timber.log.Timber
import kotlin.system.exitProcess

class NativeLib constructor(
    private val context: Context,
) {

    /**
     * A native method that is implemented by the 'lib' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    init {
        try {
            Timber.v("Loading native SYNC library")
            System.loadLibrary("ddgcrypto")
        } catch (ignored: Throwable) {
            Timber.e(ignored, "Error loading netguard library")
            exitProcess(1)
        }
    }
    fun initialize(): Int {
        return init().toInt()
    }

    private external fun init(): Long
}
