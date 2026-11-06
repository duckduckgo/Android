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

package com.duckduckgo.app.browser.tabs.transition

import android.graphics.Bitmap
import androidx.annotation.MainThread
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/** A new tab waiting to be shown: [picture] is the page it will transition over. */
data class PendingNewTabTransition(
    val picture: Deferred<Bitmap?>,
    val motion: NewTabMotion,
    val fromTabManager: Boolean = false,
)

/**
 * Hands a new tab's transition from the place that opens the tab to the browser that shows it. Matching on the tab id
 * means only that tab animates, however the tab list updates arrive in between.
 */
@MainThread
interface PendingNewTabTransitions {
    /** Replaces any earlier pending transition: only the latest new tab animates. */
    fun register(
        tabId: String,
        transition: PendingNewTabTransition,
    )

    /** Reads and consumes the pending transition for [tabId]. */
    fun consume(tabId: String): PendingNewTabTransition?

    /** Reads the pending transition for [tabId] without consuming it, unless it has expired. */
    fun peek(tabId: String): PendingNewTabTransition?

    /** The tab that was opened by tab manager, if its transition is pending. */
    fun tabManagerTabId(): String?

    fun clear()
}

/** The picture if the copy has finished and succeeded; never waits. */
@OptIn(ExperimentalCoroutinesApi::class)
fun Deferred<Bitmap?>.readyOrNull(): Bitmap? = if (isCompleted) runCatching { getCompleted() }.getOrNull() else null

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealPendingNewTabTransitions @Inject constructor(
    private val currentTimeProvider: CurrentTimeProvider,
    @param:AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : PendingNewTabTransitions {

    private var pending: Entry? = null
    private var expiry: Job? = null

    override fun register(
        tabId: String,
        transition: PendingNewTabTransition,
    ) {
        clear()
        val entry = Entry(tabId, transition, currentTimeProvider.elapsedRealtime())
        pending = entry
        // Drops the picture even if the tab never shows and nothing looks it up again.
        expiry = appCoroutineScope.launch(dispatchers.main()) {
            delay(PENDING_TTL_MS.milliseconds)
            if (pending === entry) pending = null
        }
    }

    override fun consume(tabId: String): PendingNewTabTransition? {
        val entry = current()?.takeIf { it.tabId == tabId } ?: return null
        clear()
        return entry.transition
    }

    override fun peek(tabId: String): PendingNewTabTransition? = current()?.takeIf { it.tabId == tabId }?.transition

    override fun tabManagerTabId(): String? = current()?.takeIf { it.transition.fromTabManager }?.tabId

    override fun clear() {
        expiry?.cancel()
        expiry = null
        pending = null
    }

    private fun current(): Entry? {
        val entry = pending ?: return null
        if (currentTimeProvider.elapsedRealtime() - entry.registeredAt > PENDING_TTL_MS) {
            pending = null
            return null
        }
        return entry
    }

    private data class Entry(
        val tabId: String,
        val transition: PendingNewTabTransition,
        val registeredAt: Long,
    )

    private companion object {
        const val PENDING_TTL_MS = 3_000L
    }
}
