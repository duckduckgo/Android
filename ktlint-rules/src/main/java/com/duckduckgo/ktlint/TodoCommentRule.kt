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

package com.duckduckgo.ktlint

import com.pinterest.ktlint.core.Rule
import com.pinterest.ktlint.core.ast.ElementType.EOL_COMMENT
import org.jetbrains.kotlin.com.intellij.lang.ASTNode

class TodoCommentRule : Rule("todo-comment") {
    override fun visit(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (
            offset: Int,
            errorMessage: String,
            canBeAutoCorrected: Boolean
        ) -> Unit
    ) {
        if (node.elementType == EOL_COMMENT) {
            val commentText = node.text
            if (commentText.contains("TODO")) {
                val keywordIndex = commentText.indexOf("TODO")

                if (keywordIndex > 0) {
                    val keywordCountOffset = keywordIndex + "TODO".length
                    val noColonAfter = commentText[keywordCountOffset].toString() != ":"

                    if (noColonAfter) {
                        emit(
                            node.startOffset,
                            "TODO should have a ':' immediately after",
                            true
                        )
                    }
                }
            }
        }
    }
}
