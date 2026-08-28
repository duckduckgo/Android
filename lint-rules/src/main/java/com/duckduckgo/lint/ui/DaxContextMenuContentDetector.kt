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

package com.duckduckgo.lint.ui

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.getParameterForArgument
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class DaxContextMenuContentDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = Handler(context)

    internal class Handler(private val context: JavaContext) : UElementHandler() {

        override fun visitCallExpression(node: UCallExpression) {
            if (node.methodName !in CONTEXT_MENU_COMPOSABLES) return
            check(node, "content", CONTEXT_MENU_SCOPE)
        }

        private fun check(node: UCallExpression, paramName: String, scope: String) {
            val arg = node.valueArguments.find { node.getParameterForArgument(it)?.name == paramName } ?: return
            var violation = false
            arg.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val owner = node.resolve()?.containingClass?.qualifiedName
                    if (owner != null && owner != scope) violation = true
                    // Judge only what the slot emits, so a call's own arguments are left unvisited.
                    return true
                }
            })
            if (violation) report(arg)
        }

        private fun report(arg: UExpression) {
            context.report(
                issue = INVALID_DAX_CONTEXT_MENU_CONTENT_USAGE,
                location = context.getLocation(arg),
                message = INVALID_DAX_CONTEXT_MENU_CONTENT_USAGE.getExplanation(TextFormat.RAW),
            )
        }
    }

    companion object {
        private const val CONTEXT_MENU_SCOPE = "com.duckduckgo.common.ui.compose.contextmenu.DaxContextMenuScope"
        private val CONTEXT_MENU_COMPOSABLES = setOf(
            "DaxContextMenu",
            "DaxContextMenuIconButton",
        )

        val INVALID_DAX_CONTEXT_MENU_CONTENT_USAGE: Issue = Issue
            .create(
                id = "InvalidDaxContextMenuContentUsage",
                briefDescription = "Context-menu content slot should only use DaxContextMenuScope composables",
                explanation = """
                    Use composables from DaxContextMenuScope (DefaultItem, IconItem, InsetItem) for the content
                    slot, to keep context menus consistent with the design system.
                """.trimIndent(),
                moreInfo = "",
                category = CUSTOM_LINT_CHECKS,
                priority = 6,
                severity = Severity.WARNING,
                androidSpecific = true,
                implementation = Implementation(
                    DaxContextMenuContentDetector::class.java,
                    EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                ),
            )
    }
}
