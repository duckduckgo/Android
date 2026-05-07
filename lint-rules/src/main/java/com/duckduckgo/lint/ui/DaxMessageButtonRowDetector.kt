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
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiWildcardType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor
import java.util.EnumSet

/**
 * Reports an error when a lambda passed to a `DaxMessageButtonRowScope`-receiver slot
 * contains any composable other than the four approved helpers. Triggers for the slot on
 * `DaxMessage` itself and on any wrapper that forwards a `DaxMessageButtonRowScope.() -> Unit`.
 */
@Suppress("UnstableApiUsage")
class DaxMessageButtonRowDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = Handler(context)

    internal class Handler(private val context: JavaContext) : UElementHandler() {

        override fun visitCallExpression(node: UCallExpression) {
            val resolved = node.resolve() as? PsiMethod ?: return

            resolved.parameterList.parameters.forEachIndexed { index, param ->
                if (!receivesButtonRowScope(param)) return@forEachIndexed

                val lambda = node.getArgumentForParameter(index) as? ULambdaExpression ?: return@forEachIndexed

                lambda.body.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(call: UCallExpression): Boolean {
                        val name = call.methodName ?: return true
                        if (!isAllowedHelper(call, name)) {
                            context.report(
                                issue = INVALID_BUTTON_ROW_CONTENT,
                                location = context.getLocation(call),
                                message = "Only DaxMessageButtonRowScope helpers " +
                                    "(${ALLOWED_HELPERS.joinToString()}) " +
                                    "are allowed inside the buttonRow slot.",
                            )
                        }
                        // Do not descend into the call's arguments — nested expressions in helper
                        // arguments (e.g. lambdas inside DaxAction) are out of scope for this rule.
                        return true
                    }
                })
            }
        }

        private fun isAllowedHelper(call: UCallExpression, name: String): Boolean {
            if (name !in ALLOWED_HELPERS) return false
            val resolved = call.resolve() as? PsiMethod ?: return false
            val pkg = context.evaluator.getPackage(resolved)?.qualifiedName
            return pkg == DAX_MESSAGE_PACKAGE
        }

        private fun receivesButtonRowScope(param: PsiParameter): Boolean {
            val type = param.type as? PsiClassType ?: return false
            val typeName = type.resolve()?.qualifiedName ?: return false
            val isFunctionType = typeName.startsWith("kotlin.jvm.functions.Function") ||
                typeName.startsWith("kotlin.Function")
            if (!isFunctionType) return false

            val firstArg = type.parameters.firstOrNull() ?: return false
            val receiver = when (firstArg) {
                is PsiClassType -> firstArg
                is PsiWildcardType -> firstArg.bound as? PsiClassType
                else -> null
            } ?: return false
            return receiver.resolve()?.qualifiedName == SCOPE_FQN
        }
    }

    companion object {
        private const val DAX_MESSAGE_PACKAGE = "com.duckduckgo.common.ui.compose.message"
        private const val SCOPE_FQN = "com.duckduckgo.common.ui.compose.message.DaxMessageButtonRowScope"

        private val ALLOWED_HELPERS = setOf(
            "RightAlignButtons",
            "CenterAlignedButtons",
            "FullWidthSingleButton",
            "SmallSingleButton",
        )

        val INVALID_BUTTON_ROW_CONTENT: Issue = Issue.create(
            id = "DaxMessageButtonRowContent",
            briefDescription = "Only DaxMessageButtonRowScope helpers are allowed in buttonRow",
            explanation = """
                The `buttonRow` slot on `DaxMessage` accepts only the four approved helpers: \
                `RightAlignButtons`, `CenterAlignedButtons`, `FullWidthSingleButton`, and \
                `SmallSingleButton`. Other composables (raw `Text`, `Row`, custom buttons, \
                etc.) bypass the design-system constraints and must not be used here.
            """.trimIndent(),
            moreInfo = "",
            category = CUSTOM_LINT_CHECKS,
            priority = 5,
            severity = Severity.ERROR,
            androidSpecific = true,
            implementation = Implementation(
                DaxMessageButtonRowDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
            ),
        )
    }
}
