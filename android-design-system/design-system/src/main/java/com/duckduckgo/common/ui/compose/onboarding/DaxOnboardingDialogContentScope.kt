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

package com.duckduckgo.common.ui.compose.onboarding

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.layout.VerticalAlignmentLine

/**
 * Receiver scope for arbitrary dialog content.
 *
 * The dialog enforces pointer, focus and semantics gating for the whole slot. [enabled] is a
 * reporting value that lets content choose its own visual state; it is not the enforcement
 * boundary. Register long-lived work with [onContentReady] and cancel it from a `DisposableEffect`.
 * The slot is keyed by content key, so its remembered state and effects are disposed when the
 * content key changes.
 */
@Stable
class DaxOnboardingDialogContentScope internal constructor(
    private val isEnabled: () -> Boolean,
) : ColumnScope {
    private var columnScope: ColumnScope? = null
    private var pendingReady: Boolean? = null
    private var readyCallback: ((Boolean) -> Unit)? = null
    private var delivered = false
    internal var readyVersion by mutableIntStateOf(0)
        private set

    /** Whether the shared intro has settled and content may be presented as ready. */
    val enabled: Boolean
        get() = isEnabled()

    /**
     * Registers a callback invoked once after this content key's shared content fade (or snap).
     * `animated` is false when the intro was snapped or skipped.
     * The registration is dropped if the slot leaves composition before then.
     */
    fun onContentReady(callback: (animated: Boolean) -> Unit) {
        val firstRegistration = readyCallback == null
        readyCallback = callback
        if (firstRegistration && pendingReady != null && !delivered) readyVersion++
    }

    internal fun notifyContentReady(animated: Boolean) {
        if (delivered) return
        pendingReady = animated
        readyVersion++
    }

    internal fun clearReadyCallback() {
        readyCallback = null
    }

    internal fun deliverContentReady() {
        if (delivered) return
        val animated = pendingReady ?: return
        val callback = readyCallback ?: return
        delivered = true
        pendingReady = null
        callback(animated)
    }

    internal fun installColumnScope(scope: ColumnScope) {
        columnScope = scope
    }

    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier {
        val base = this
        return columnScope?.let { with(it) { base.weight(weight, fill) } } ?: base
    }

    override fun Modifier.align(alignment: Alignment.Horizontal): Modifier {
        val base = this
        return columnScope?.let { with(it) { base.align(alignment) } } ?: base
    }

    override fun Modifier.alignBy(alignmentLine: VerticalAlignmentLine): Modifier {
        val base = this
        return columnScope?.let { with(it) { base.alignBy(alignmentLine) } } ?: base
    }

    override fun Modifier.alignBy(alignmentLineBlock: (Measured) -> Int): Modifier {
        val base = this
        return columnScope?.let { with(it) { base.alignBy(alignmentLineBlock) } } ?: base
    }
}
