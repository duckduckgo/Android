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

package com.duckduckgo.macos_impl.waitlist.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.ReceiverScope
import com.duckduckgo.macos_impl.MacOsPixelNames.MACOS_WAITLIST_SHARE_SHARED
import dagger.android.AndroidInjection
import javax.inject.Inject

@InjectWith(ReceiverScope::class)
class MacOsInviteShareBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var pixel: Pixel

    override fun onReceive(context: Context, intent: Intent) {
        AndroidInjection.inject(this, context)
        pixel.fire(MACOS_WAITLIST_SHARE_SHARED)
    }
}
