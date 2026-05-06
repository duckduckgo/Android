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

package com.duckduckgo.duckchat.impl.helper

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

interface PendingDuckChatOpenActionStore {
    fun markOpenSidebar()
    fun consumeOpenSidebar(): Boolean
    fun clear()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPendingDuckChatOpenActionStore @Inject constructor() : PendingDuckChatOpenActionStore {

    private val openSidebarPending = AtomicBoolean(false)

    override fun markOpenSidebar() {
        openSidebarPending.set(true)
    }

    override fun consumeOpenSidebar(): Boolean = openSidebarPending.getAndSet(false)

    override fun clear() {
        openSidebarPending.set(false)
    }
}
