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

package com.duckduckgo.duckchat.impl.ui.nativeinput.textselection

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class TextSelection(
    val id: String,
    val text: String,
    val url: String,
)

interface TextSelectionStore {
    fun selections(tabId: String): StateFlow<List<TextSelection>>
    fun add(tabId: String, text: String, url: String): Boolean
    fun consume(tabId: String): List<TextSelection>
    fun remove(tabId: String, id: String)

    companion object {
        const val MAX_SELECTIONS = 5
    }
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealTextSelectionStore @Inject constructor() : TextSelectionStore {

    private val selections = ConcurrentHashMap<String, MutableStateFlow<List<TextSelection>>>()

    override fun selections(tabId: String): StateFlow<List<TextSelection>> = getFlow(tabId)

    override fun add(tabId: String, text: String, url: String): Boolean {
        val selection = getTextSelection(text, url) ?: return false
        val selections = getFlow(tabId).updateAndGet { existing ->
            val isDuplicate = existing.any { it.text == selection.text }
            if (isDuplicate || existing.size >= TextSelectionStore.MAX_SELECTIONS) existing else existing + selection
        }
        return selections.any { it.text == selection.text }
    }

    override fun consume(tabId: String): List<TextSelection> = getFlow(tabId).getAndUpdate { emptyList() }

    override fun remove(tabId: String, id: String) {
        getFlow(tabId).update { current -> current.filterNot { it.id == id } }
    }

    private fun getFlow(tabId: String): MutableStateFlow<List<TextSelection>> =
        selections.computeIfAbsent(tabId) { MutableStateFlow(emptyList()) }

    private fun getTextSelection(text: String, url: String): TextSelection? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return TextSelection(UUID.randomUUID().toString(), trimmed, url)
    }
}
