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
class DaxListItemContentDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = Handler(context)

    internal class Handler(private val context: JavaContext) : UElementHandler() {

        private val leadingMembers by lazy { membersOf(LEADING_SCOPE) }
        private val trailingMembers by lazy { membersOf(TRAILING_SCOPE) }

        override fun visitCallExpression(node: UCallExpression) {
            if (node.methodName !in LIST_ITEM_COMPOSABLES) return
            check(node, "leadingContent", leadingMembers)
            check(node, "trailingContent", trailingMembers)
        }

        private fun check(node: UCallExpression, paramName: String, allowed: List<String>) {
            if (allowed.isEmpty()) return
            val arg = node.valueArguments.find { node.getParameterForArgument(it)?.name == paramName } ?: return
            val violations = mutableListOf<String>()
            arg.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    val name = node.methodName
                    if (name != null && name.firstOrNull()?.isUpperCase() == true && name !in allowed) violations += name
                    return super.visitCallExpression(node)
                }
            })
            if (violations.isNotEmpty()) report(arg)
        }

        private fun membersOf(fqcn: String): List<String> =
            context.evaluator.findClass(fqcn)?.methods?.filterNot { it.isConstructor }?.mapNotNull { it.name } ?: emptyList()

        private fun report(arg: UExpression) {
            context.report(
                issue = INVALID_DAX_LIST_ITEM_CONTENT_USAGE,
                location = context.getLocation(arg),
                message = INVALID_DAX_LIST_ITEM_CONTENT_USAGE.getExplanation(TextFormat.RAW),
            )
        }
    }

    companion object {
        private const val LEADING_SCOPE = "com.duckduckgo.common.ui.compose.listitem.DaxListItemLeadingScope"
        private const val TRAILING_SCOPE = "com.duckduckgo.common.ui.compose.listitem.DaxListItemTrailingScope"
        private val LIST_ITEM_COMPOSABLES = setOf("DaxOneLineListItem", "DaxTwoLineListItem", "DaxSettingsListItem")

        val INVALID_DAX_LIST_ITEM_CONTENT_USAGE: Issue = Issue
            .create(
                id = "InvalidDaxListItemContentUsage",
                briefDescription = "List-item leading/trailing slots should only use DaxListItem*Scope composables",
                explanation = """
                    Use composables from DaxListItemLeadingScope / DaxListItemTrailingScope for the
                    leadingContent / trailingContent slots, to keep list items consistent with the design system.
                """.trimIndent(),
                moreInfo = "",
                category = CUSTOM_LINT_CHECKS,
                priority = 6,
                severity = Severity.WARNING,
                androidSpecific = true,
                implementation = Implementation(
                    DaxListItemContentDetector::class.java,
                    EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                ),
            )
    }
}
