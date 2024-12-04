/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.service

import android.app.assist.AssistStructure
import android.app.assist.AssistStructure.ViewNode
import android.app.assist.AssistStructure.WindowNode
import android.service.autofill.FillRequest
import android.util.Pair
import android.view.ViewStructure.HtmlInfo
import android.view.autofill.AutofillId
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.inline.InlinePresentationSpec
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

fun fillRequest(): FillRequest = mock()

fun inlineSuggestionsRequest(): InlineSuggestionsRequest = mock()

fun inlinePresentationSpec(): InlinePresentationSpec = mock()

fun FillRequest.inlineSuggestionsRequest(suggestionsRequest: InlineSuggestionsRequest): FillRequest {
    whenever(this.inlineSuggestionsRequest).thenReturn(suggestionsRequest)
    return this
}

fun InlineSuggestionsRequest.maxSuggestionCount(count: Int): InlineSuggestionsRequest {
    whenever(this.maxSuggestionCount).thenReturn(count)
    return this
}

fun InlineSuggestionsRequest.inlinePresentationSpecs(vararg specs: InlinePresentationSpec): InlineSuggestionsRequest {
    whenever(this.inlinePresentationSpecs).thenReturn(specs.toMutableList())
    return this
}

fun assistStructure(): AssistStructure = mock()

fun AssistStructure.windowNodes(vararg windowNodes: WindowNode): AssistStructure {
    whenever(this.windowNodeCount).thenReturn(windowNodes.size)
    windowNodes.forEachIndexed { index, windowNode ->
        whenever(this.getWindowNodeAt(index)).thenReturn(windowNode)
    }
    return this
}

fun windowNode(): WindowNode = mock()

fun WindowNode.rootViewNode(viewNode: ViewNode): WindowNode {
    whenever(this.rootViewNode).thenReturn(viewNode)
    return this
}

fun autofillId(): AutofillId = mock()

fun viewNode(): ViewNode {
    return mock()
}

fun ViewNode.webDomain(domain: String): ViewNode {
    whenever(this.webDomain).thenReturn(domain)
    return this
}

fun ViewNode.autofillId(id: AutofillId): ViewNode {
    whenever(this.autofillId).thenReturn(id)
    return this
}

fun ViewNode.packageId(id: String): ViewNode {
    whenever(this.idPackage).thenReturn(id)
    return this
}

fun ViewNode.childrenNodes(vararg viewNodes: ViewNode): ViewNode {
    whenever(this.childCount).thenReturn(viewNodes.size)
    viewNodes.forEachIndexed { index, viewNode ->
        whenever(this.getChildAt(index)).thenReturn(viewNode)
    }
    return this
}

fun ViewNode.autofillHints(hints: Array<String>?): ViewNode {
    whenever(this.autofillHints).thenReturn(hints)
    return this
}

fun ViewNode.idEntry(id: String): ViewNode {
    whenever(this.idEntry).thenReturn(id)
    return this
}

fun ViewNode.hint(hint: String): ViewNode {
    whenever(this.hint).thenReturn(hint)
    return this
}

fun ViewNode.htmlAttributes(attributes: List<Pair<String, String>>): ViewNode {
    val htmlInfoMock = mock<HtmlInfo>()
    whenever(this.htmlInfo).thenReturn(htmlInfoMock)
    whenever(htmlInfoMock.attributes).thenReturn(attributes)
    return this
}
