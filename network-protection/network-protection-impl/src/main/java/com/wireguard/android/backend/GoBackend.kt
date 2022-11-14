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

package com.wireguard.android.backend

import android.content.Context
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.library.loader.LibraryLoader
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import timber.log.Timber
import javax.inject.Inject
import kotlin.system.exitProcess

@SingleInstanceIn(VpnScope::class)
class GoBackend @Inject constructor(context: Context) {
    init {
        try {
            Timber.v("Loading wireguard-go library")
            LibraryLoader.loadLibrary(context, "wg-go")
        } catch (ignored: Throwable) {
            Timber.e(ignored, "Error loading wireguard-go library")
            exitProcess(1)
        }
    }

    external fun wgGetConfig(handle: Int): String?

    external fun wgGetSocketV4(handle: Int): Int

    external fun wgGetSocketV6(handle: Int): Int

    external fun wgTurnOff(handle: Int)

    external fun wgTurnOn(
        ifName: String,
        tunFd: Int,
        settings: String
    ): Int

    external fun wgVersion(): String
}
