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

package com.duckduckgo.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UNamedExpression
import org.jetbrains.uast.UastFacade
import java.util.EnumSet

@Suppress("UnstableApiUsage")
class MetricsPixelNumericValueDetector : Detector(), SourceCodeScanner {

    // Path 1: fires when the constructor can be resolved to a PsiMethod (source stubs in tests).
    override fun getApplicableConstructorTypes() = listOf(METRICS_PIXEL_FQN)

    override fun visitConstructor(context: JavaContext, node: UCallExpression, constructor: PsiMethod) {
        checkNode(context, node)
    }

    // Path 2: fires for all UCallExpressions. Used as a fallback when getApplicableConstructorTypes
    // doesn't trigger — which happens for compiled Kotlin data classes whose constructor PsiMethod
    // can't be resolved from a dependency JAR.
    override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            // Skip if constructor is resolvable — Path 1 (visitConstructor) handles it.
            if (node.resolve() != null) return
            // Use source text as a reliable name filter since UAST name resolution may be
            // unavailable when the constructor can't be resolved from a compiled JAR.
            val sourceText = node.sourcePsi?.text ?: return
            if (!sourceText.startsWith("MetricsPixel(")) return
            val fqn = node.returnType?.canonicalText
            if (fqn != null && fqn != METRICS_PIXEL_FQN) return
            checkNode(context, node)
        }
    }

    private fun checkNode(context: JavaContext, node: UCallExpression) {
        val typeText = findArgText(node, "type") ?: return
        if (!typeText.contains("COUNT_WHEN_IN_WINDOW") && !typeText.contains("COUNT_ALWAYS")) return

        val valueText = findArgText(node, "value") ?: return
        // Strip surrounding quotes from a string literal so we can check if it's an integer.
        val valueStr = valueText.removeSurrounding("\"")

        if (valueStr.toIntOrNull() == null) {
            // Report on the value argument expression; fall back to the call site if the
            // expression can't be resolved (e.g. UastFacade.convertElement fails in Path 2).
            val valueExpr: UExpression = findArgExpr(node, "value") ?: node
            context.report(
                NUMERIC_VALUE_REQUIRED,
                valueExpr,
                context.getLocation(valueExpr),
                "`value` must be a valid integer string for COUNT_WHEN_IN_WINDOW/COUNT_ALWAYS pixel types (got \"$valueStr\")",
            )
        }
    }

    /**
     * Returns the source text of a named Kotlin argument, unwrapping any [UNamedExpression]
     * wrapper. Falls back to Kotlin PSI for reliable named-argument lookup when UAST does not
     * emit [UNamedExpression] wrappers (observed for compiled-class constructor calls in real
     * lint runs where valueArguments are in source order without name info).
     */
    private fun findArgText(node: UCallExpression, name: String): String? {
        // UAST named-argument lookup (works when valueArguments contain UNamedExpression).
        for (arg in node.valueArguments) {
            if (arg is UNamedExpression && arg.name == name) {
                return arg.expression.sourcePsi?.text
            }
        }
        // Kotlin PSI fallback: directly reads the named argument from the Kotlin parse tree,
        // which is reliable regardless of argument order and UAST wrapper availability.
        val ktCall = node.sourcePsi as? KtCallExpression ?: return null
        return ktCall.valueArguments
            .find { it.getArgumentName()?.asName?.identifier == name }
            ?.getArgumentExpression()
            ?.text
    }

    /**
     * Returns the UAST [UExpression] for a named argument for use as a lint report location.
     * Mirrors [findArgText] but returns the expression node instead of text.
     */
    private fun findArgExpr(node: UCallExpression, name: String): UExpression? {
        for (arg in node.valueArguments) {
            if (arg is UNamedExpression && arg.name == name) return arg.expression
        }
        val ktCall = node.sourcePsi as? KtCallExpression ?: return null
        val ktExpr = ktCall.valueArguments
            .find { it.getArgumentName()?.asName?.identifier == name }
            ?.getArgumentExpression() ?: return null
        return UastFacade.convertElement(ktExpr, null) as? UExpression
    }

    companion object {
        private const val METRICS_PIXEL_FQN = "com.duckduckgo.feature.toggles.api.MetricsPixel"

        val NUMERIC_VALUE_REQUIRED = Issue.create(
            "MetricsPixelNumericValue",
            "MetricsPixel value must be a numeric string for counting types",
            "When MetricType is COUNT_WHEN_IN_WINDOW or COUNT_ALWAYS, the `value` field is " +
                "called with `.toInt()` at runtime. Providing a non-integer string will crash.",
            Category.CORRECTNESS,
            10,
            Severity.ERROR,
            Implementation(
                MetricsPixelNumericValueDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
            ),
        )
    }
}