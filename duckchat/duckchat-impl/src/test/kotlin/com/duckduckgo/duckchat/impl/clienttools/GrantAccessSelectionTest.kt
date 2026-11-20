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
    fun whenRemovingIsOffThenNoToolCanBeUnchecked() {
        val selection = selection(removable = false, "a", "b", "c")

        assertFalse(selection.showsCheckboxes)
        assertFalse(selection.canToggle("a"))
        assertEquals(selection, selection.toggle("a"))
    }

    @Test
    fun whenOnlyOneToolIsAskedThenItCannotBeUnchecked() {
        val selection = selection(removable = true, "a")

        assertFalse(selection.showsCheckboxes)
        assertTrue(selection.toggle("a").isChecked("a"))
    }

    @Test
    fun whenPromptOpensThenEveryToolIsChecked() {
        val selection = selection(removable = true, "a", "b", "c")

        assertTrue(selection.showsCheckboxes)
        assertTrue(listOf("a", "b", "c").all { selection.isChecked(it) })
        assertTrue(selection.removed.isEmpty())
    }

    @Test
    fun whenToolUncheckedThenItIsLeftOutOfTheAnswer() {
        val selection = selection(removable = true, "a", "b", "c").toggle("b")

        assertFalse(selection.isChecked("b"))
        assertEquals(setOf("b"), selection.removed)
    }

    @Test
    fun whenToolCheckedAgainThenItIsBackInTheAnswer() {
        val selection = selection(removable = true, "a", "b", "c").toggle("b").toggle("b")

        assertTrue(selection.isChecked("b"))
        assertTrue(selection.removed.isEmpty())
    }

    @Test
    fun whenOneToolStaysCheckedThenItCannotBeUnchecked() {
        val selection = selection(removable = true, "a", "b").toggle("a")

        assertFalse(selection.canToggle("b"))
        assertTrue(selection.toggle("b").isChecked("b"))
        assertTrue(selection.canToggle("a"))
    }

    @Test
    fun whenToolIsNotAskedThenItCannotBeToggled() {
        val selection = selection(removable = true, "a", "b")

        assertEquals(selection, selection.toggle("c"))
    }

    @Test
    fun whenBuiltFromRequestThenItFollowsTheRequestFlag() {
        val tools = listOf(tool("a"), tool("b"))

        assertTrue(
            GrantAccessSelection(ClientToolPermissionRequest.GrantAccess("tab", BrowserMode.REGULAR, tools, toolsRemovable = true)).showsCheckboxes,
        )
        assertFalse(
            GrantAccessSelection(ClientToolPermissionRequest.GrantAccess("tab", BrowserMode.REGULAR, tools, toolsRemovable = false)).showsCheckboxes,
        )
    }

    private fun selection(
        removable: Boolean,
        vararg names: String,
    ) = GrantAccessSelection(names.map { tool(it) }, removable)

    private fun tool(name: String) = AskingTool(name, ClientToolPermissionMode.Ask(1, 2, 3))
}

class PendingClientToolPermissionSelectionTest {

    @Test
    fun whenToolUncheckedThenThePromptKeepsItOut() {
        val prompt = pending("a", "b")

        prompt.toggle("a")

        assertEquals(setOf("a"), prompt.selection?.removed)
    }

    @Test
    fun whenPromptOpensThenItStartsWithEveryTool() {
        assertEquals(emptySet<String>(), pending("a", "b").selection?.removed)
    }

    @Test
    fun whenPromptAsksForOneToolToRunThenThereIsNoSelection() {
        val runTool = PendingClientToolPermission(
            ClientToolPermissionRequest.RunTool("tab", BrowserMode.REGULAR, AskingTool("a", ClientToolPermissionMode.Ask(1, 2, 3))),
            CompletableDeferred(),
        )

        runTool.toggle("a")

        assertNull(runTool.selection)
    }

    private fun pending(vararg names: String) = PendingClientToolPermission(
        ClientToolPermissionRequest.GrantAccess(
            "tab",
            BrowserMode.REGULAR,
            names.map { AskingTool(it, ClientToolPermissionMode.Ask(1, 2, 3)) },
            toolsRemovable = true,
        ),
        CompletableDeferred(),
    )
}
