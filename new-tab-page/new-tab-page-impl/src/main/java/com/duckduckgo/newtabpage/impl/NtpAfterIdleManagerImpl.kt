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

package com.duckduckgo.newtabpage.impl

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.api.NtpAfterIdleManager
import com.duckduckgo.newtabpage.impl.pixels.HatchPixels
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class NtpAfterIdleManagerImpl @Inject constructor(
    private val hatchPixels: HatchPixels,
) : NtpAfterIdleManager {

    private val afterIdle = AtomicBoolean(false)

    override fun setAfterIdle(isAfterIdle: Boolean) {
        afterIdle.set(isAfterIdle)
    }

    override fun wasAfterIdle(): Boolean = afterIdle.get()

    override fun fireReturnToPageTapped() {
        hatchPixels.fireReturnToPageTapped(wasAfterIdle())
    }
}
