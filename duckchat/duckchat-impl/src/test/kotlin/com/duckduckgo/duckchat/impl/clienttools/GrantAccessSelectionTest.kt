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

package com.duckduckgo.duckchat.impl.clienttools

import com.duckduckgo.browsermode.api.BrowserMode
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrantAccessSelectionTest {

    @Test
    fun whenRemovingIsOffThenNoToolCanBeRemoved() {
        val selection = selection(removable = false, "a", "b", "c")

        assertFalse(selection.showsBoxes)
        assertFalse(selection.canRemove)
        assertEquals(selection, selection.remove("a"))
    }

    @Test
    fun whenOnlyOneToolIsAskedThenItCannotBeRemoved() {
        val selection = selection(removable = true, "a")

        assertFalse(selection.showsBoxes)
        assertFalse(selection.canRemove)
        assertEquals(listOf("a"), selection.remove("a").remaining.names())
    }

    @Test
    fun whenToolRemovedThenItLeavesTheListInAskedOrder() {
        val selection = selection(removable = true, "a", "b", "c").remove("b")

        assertEquals(listOf("a", "c"), selection.remaining.names())
        assertEquals(listOf("b"), selection.removedTools.names())
        assertEquals(setOf("b"), selection.removed)
    }

    @Test
    fun whenOneToolRemainsThenItCannotBeRemoved() {
        val selection = selection(removable = true, "a", "b").remove("a")

        assertTrue(selection.showsBoxes)
        assertFalse(selection.canRemove)
        assertEquals(listOf("b"), selection.remove("b").remaining.names())
    }

    @Test
    fun whenUndoThenEveryToolIsBack() {
        val selection = selection(removable = true, "a", "b", "c").remove("a").remove("c").undo()

        assertEquals(listOf("a", "b", "c"), selection.remaining.names())
        assertTrue(selection.removed.isEmpty())
    }

    @Test
    fun whenBuiltFromRequestThenItFollowsTheRequestFlag() {
        val tools = listOf(tool("a"), tool("b"))

        assertTrue(GrantAccessSelection(ClientToolPermissionRequest.GrantAccess("tab", BrowserMode.REGULAR, tools, toolsRemovable = true)).canRemove)
        assertFalse(
            GrantAccessSelection(ClientToolPermissionRequest.GrantAccess("tab", BrowserMode.REGULAR, tools, toolsRemovable = false)).canRemove,
        )
    }

    @Test
    fun whenJoiningNamesThenListReadsNaturally() {
        fun join(vararg names: String) = GrantAccessSelection.joinNames(names.toList(), series = { a, b -> "$a, $b" }, pair = { a, b -> "$a and $b" })

        assertEquals("A", join("A"))
        assertEquals("A and B", join("A", "B"))
        assertEquals("A, B and C", join("A", "B", "C"))
        assertEquals("A, B, C and D", join("A", "B", "C", "D"))
    }

    private fun selection(
        removable: Boolean,
        vararg names: String,
    ) = GrantAccessSelection(names.map { tool(it) }, removable)

    private fun tool(name: String) = AskingTool(name, ClientToolPermissionMode.Ask(1, 2, 3, 4))

    private fun List<AskingTool>.names() = map { it.name }
}

class GrantAccessSelectionsTest {

    private val selections = GrantAccessSelections()

    @Test
    fun whenSamePromptShownAgainThenRemovedToolsStayRemoved() {
        val prompt = pending("a", "b")
        selections.update(prompt) { it.remove("a") }

        assertEquals(setOf("a"), selections.of(prompt)?.removed)
    }

    @Test
    fun whenAnotherPromptArrivesThenItStartsWithEveryTool() {
        selections.update(pending("a", "b")) { it.remove("a") }

        assertEquals(emptySet<String>(), selections.of(pending("a", "b"))?.removed)
    }

    @Test
    fun whenPromptAsksForOneToolToRunThenThereIsNoSelection() {
        val runTool = PendingClientToolPermission(
            ClientToolPermissionRequest.RunTool("tab", BrowserMode.REGULAR, AskingTool("a", ClientToolPermissionMode.Ask(1, 2, 3, 4))),
            CompletableDeferred(),
        )

        assertNull(selections.of(runTool))
    }

    private fun pending(vararg names: String) = PendingClientToolPermission(
        ClientToolPermissionRequest.GrantAccess(
            "tab",
            BrowserMode.REGULAR,
            names.map { AskingTool(it, ClientToolPermissionMode.Ask(1, 2, 3, 4)) },
            toolsRemovable = true,
        ),
        CompletableDeferred(),
    )
}
